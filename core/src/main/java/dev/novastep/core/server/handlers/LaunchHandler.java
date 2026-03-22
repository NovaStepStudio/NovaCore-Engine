package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.minecraft.MinecraftLauncher;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.server.LaunchRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class LaunchHandler implements HttpHandler {

    private final MinecraftLauncher launcher;

    public LaunchHandler(MinecraftLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange)) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method) && path.equals("/launch")) {
            handleLaunch(exchange);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && path.startsWith("/launch/kill/")) {
            String launchId = path.substring("/launch/kill/".length());
            handleKill(exchange, launchId);
            return;
        }

        if ("GET".equalsIgnoreCase(method) && path.startsWith("/launch/status/")) {
            String launchId = path.substring("/launch/status/".length());
            handleStatus(exchange, launchId);
            return;
        }

        HttpUtils.notFound(exchange, "Unknown launch endpoint: " + path);
    }

    private void handleLaunch(HttpExchange exchange) throws IOException {
        String body = HttpUtils.readBody(exchange);
        if (body == null || body.isBlank()) {
            HttpUtils.badRequest(exchange, "Body vacío");
            return;
        }

        LaunchRequest req;
        try {
            req = HttpUtils.GSON.fromJson(body, LaunchRequest.class);
        } catch (Exception e) {
            HttpUtils.badRequest(exchange, "JSON inválido: " + e.getMessage());
            return;
        }

        if (req == null) {
            HttpUtils.badRequest(exchange, "Body nulo");
            return;
        }

        String validationError = req.validate();
        if (validationError != null) {
            HttpUtils.badRequest(exchange, validationError);
            return;
        }

        String launchId = launcher.launch(req);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("launchId", launchId);
        response.put("status", "launching");
        response.put("version", req.version);
        response.put("username", req.resolvedUsername());
        response.put("instancePath", req.resolvedInstancePath());
        response.put("authlibInjector", req.isAuthlibEnabled()
            ? Map.of("enabled", true, "server", req.authlibInjector.serverUrl)
            : Map.of("enabled", false)
        );
        response.put("message", "Minecraft launching. Conectate al WS para ver los logs en tiempo real.");
        response.put("kill", "/launch/kill/" + launchId);

        HttpUtils.accepted(exchange, response);
    }

    private void handleKill(HttpExchange exchange, String launchId) throws IOException {
        if (!HttpUtils.requireMethod(exchange, "POST")) return;

        boolean killed = launcher.kill(launchId);
        if (killed) {
            HttpUtils.ok(exchange, Map.of(
                "launchId", launchId,
                "status", "killed"
            ));
        } else {
            HttpUtils.notFound(exchange, "Proceso no encontrado o ya terminado: " + launchId);
        }
    }

    private void handleStatus(HttpExchange exchange, String launchId) throws IOException {
        if (!HttpUtils.requireMethod(exchange, "GET")) return;

        boolean running = launcher.isRunning(launchId);
        HttpUtils.ok(exchange, Map.of(
            "launchId", launchId,
            "running", running,
            "status", running ? "running" : "stopped"
        ));
    }
}