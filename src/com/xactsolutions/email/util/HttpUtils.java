package com.xactsolutions.email.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpUtils {

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2)
                map.put(keyValue[0], keyValue[1]);
        }
        return map;
    }

    public static String getPathVariable(String urlPath, String endpointInitial) {
        if (!urlPath.startsWith(endpointInitial)) return null;

        String pathVariable = urlPath.substring(endpointInitial.length());
        int i = pathVariable.indexOf("/");
        if (i != -1) pathVariable = pathVariable.substring(0, i);
        return pathVariable;
    }

    public static void setResponse(int status, String response, HttpExchange exchange) throws IOException {
        if (status < 200 || status > 599) throw new RuntimeException("Invalid response status " + status);
        if (Utils.isEmpty(response)) {
            exchange.sendResponseHeaders(status, -1);
            exchange.getResponseBody().close();
            return;
        }
        if (!response.startsWith("{") || !response.endsWith("}") || !response.contains("\"") || !response.contains(":"))
            throw new RuntimeException("Response content is not application/json!!");

        byte[] responseBytes = response.getBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.getResponseBody().close();
    }

}
