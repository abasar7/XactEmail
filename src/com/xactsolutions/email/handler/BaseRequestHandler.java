package com.xactsolutions.email.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.xactsolutions.email.util.Utils;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BaseRequestHandler implements HttpHandler {

    private final String endpoint;

    protected String path;
    protected String key;
    protected Map<String, String> queryMap;
    protected String payloadStr;

    @NoArgsConstructor @AllArgsConstructor
    protected static abstract class Response {
        int status;
    }
    @NoArgsConstructor
    protected static class JsonResponse extends Response {
        String jsonStr;
        public JsonResponse(int status, String jsonStr) {
            super(status);
            this.jsonStr = jsonStr;
        }
    }
    @NoArgsConstructor
    protected static class ByteResponse extends Response {
        byte[] bytes;
        String contentType;
        String disposition; // inline / attachment
        String filename; // optional with disposition type attachment
        public ByteResponse(int status, byte[] bytes) {
            this(status, bytes, "application/octet-stream");
        }
        public ByteResponse(int status, @NonNull byte[] bytes, @NonNull String contentType) {
            this(status, bytes, contentType, "inline");
        }
        public ByteResponse(int status, @NonNull byte[] bytes, @NonNull String contentType, @NonNull String disposition) {
            this(status, bytes, contentType, disposition, null);
        }
        public ByteResponse(int status, @NonNull byte[] bytes, @NonNull String contentType, @NonNull String disposition, String filename) {
            super(status);
            this.bytes = bytes;
            this.contentType = contentType;
            this.disposition = disposition;
            this.filename = filename;
        }
    }


    public BaseRequestHandler(String endpoint) {
        this.endpoint = endpoint;
    }


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        path = uri.getPath();
        key = getPathVariable(path, endpoint + "/");
        queryMap = getQueryMap(uri.getQuery());
        payloadStr = new String(exchange.getRequestBody().readAllBytes());
        log.debug("Received new {} request at {} with payload... \n{}", method, path, payloadStr);

        try {
            Response response = switch (method) {
                case "GET" -> {
                    if (path.equals(endpoint) || path.equals(endpoint + "/"))
                        yield list();
                    else
                        yield getOne();
                }
                case "POST" -> post();
                case "DELETE" -> delete();
                default -> throw new IllegalStateException("Unsupported HTTP method: " + method);
            };
            setResponse(exchange, response);
        } catch (Exception e) {
            log.error("Exception in {} endpoint", endpoint, e);
            setResponse(exchange, new JsonResponse(500, null));
        }
    }

    protected @NonNull Response list() {
        return new JsonResponse(405, null);
    }

    protected @NonNull Response getOne() {
        return new JsonResponse(405, null);
    }

    protected @NonNull Response post() {
        return new JsonResponse(405, null);
    }

    protected @NonNull Response delete() {
        return new JsonResponse(405, null);
    }


    private static String getPathVariable(String urlPath, String endpointInitial) {
        if (!urlPath.startsWith(endpointInitial)) return null;

        String pathVariable = urlPath.substring(endpointInitial.length());
        int i = pathVariable.indexOf("/");
        if (i != -1) pathVariable = pathVariable.substring(0, i);
        return pathVariable;
    }

    public static @NonNull Map<String, String> getQueryMap(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;

        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2)
                map.put(keyValue[0], keyValue[1]);
        }
        return map;
    }

    private static void setResponse(HttpExchange exchange, Response response) throws IOException {
        int status = response.status;
        boolean isJsonResponse = response instanceof JsonResponse;
        String jsonStr = null;
        if (isJsonResponse) jsonStr = ((JsonResponse)response).jsonStr;

        if (status < 200 || status > 599) throw new RuntimeException("Invalid response status " + status);
        if (status == 204 || (isJsonResponse && Utils.isEmpty(jsonStr))) {
            exchange.sendResponseHeaders(response.status, -1);
            exchange.getResponseBody().close();
            return;
        }

        if (isJsonResponse) {
            if (!jsonStr.startsWith("{") || !jsonStr.endsWith("}") || !jsonStr.contains("\"") || !jsonStr.contains(":"))
                throw new RuntimeException("Response content is not application/json!!");

            byte[] responseBytes = jsonStr.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        } else if (response instanceof ByteResponse byteResponse) {
            exchange.getResponseHeaders().add("Content-Type", byteResponse.contentType);
            if (byteResponse.disposition.equals("inline") || Utils.isEmpty(byteResponse.filename)) {
                exchange.getResponseHeaders().add("Content-Disposition", byteResponse.disposition);
            } else if (byteResponse.disposition.equals("attachment") && !Utils.isEmpty(byteResponse.filename)) {
                exchange.getResponseHeaders()
                    .add("Content-Disposition", "attachment; filename=\""+byteResponse.filename+"\"");
            }
            exchange.sendResponseHeaders(status, byteResponse.bytes.length);
            exchange.getResponseBody().write(byteResponse.bytes);
        }
        exchange.getResponseBody().close();
    }

}
