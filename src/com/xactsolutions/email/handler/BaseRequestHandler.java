package com.xactsolutions.email.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.xactsolutions.email.util.HttpUtils;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class BaseRequestHandler implements HttpHandler {

    private final String endpoint;

    protected String path;
    protected String key;
    protected String payloadStr;

    @NoArgsConstructor @AllArgsConstructor
    protected static class Response {
        int status;
        String responseStr;

    }

    public BaseRequestHandler(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        path = exchange.getRequestURI().getPath();
        key = HttpUtils.getPathVariable(path, endpoint + "/");
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
            HttpUtils.setResponse(response.status, response.responseStr, exchange);
        } catch (Exception e) {
            log.error("Exception in {} endpoint", endpoint, e);
            HttpUtils.setResponse(500, null, exchange);
        }
    }

    protected @NonNull Response list() {
        return new Response(405, null);
    }

    protected @NonNull Response getOne() {
        return new Response(405, null);
    }

    protected @NonNull Response post() {
        return new Response(405, null);
    }

    protected @NonNull Response delete() {
        return new Response(405, null);
    }

}
