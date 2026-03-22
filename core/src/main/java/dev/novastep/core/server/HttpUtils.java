package dev.novastep.core.server;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpUtils {

    public static final Gson GSON = new Gson();

    private HttpUtils() {}

    public static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void ok(HttpExchange exchange, Object body) throws IOException {
        sendJson(exchange, 200, body);
    }

    public static void accepted(HttpExchange exchange, Object body) throws IOException {
        sendJson(exchange, 202, body);
    }

    public static void badRequest(HttpExchange exchange, String error) throws IOException {
        sendJson(exchange, 400, java.util.Map.of("error", error, "status", 400));
    }

    public static void notFound(HttpExchange exchange, String error) throws IOException {
        sendJson(exchange, 404, java.util.Map.of("error", error, "status", 404));
    }

    public static void methodNotAllowed(HttpExchange exchange) throws IOException {
        sendJson(exchange, 405, java.util.Map.of(
            "error", "Method not allowed: " + exchange.getRequestMethod(),
            "status", 405
        ));
    }

    public static void serverError(HttpExchange exchange, String error) throws IOException {
        sendJson(exchange, 500, java.util.Map.of("error", error, "status", 500));
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) return null;

        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            if (k.equals(key)) return v;
        }
        return null;
    }

    public static boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
            methodNotAllowed(exchange);
            return false;
        }
        return true;
    }

    public static boolean handleCors(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }
}