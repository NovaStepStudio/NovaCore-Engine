package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.minecraft.MinecraftLauncher;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.server.LaunchRequest;

import java.io.IOException;
import java.util.*;

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
            handleKill(exchange, path.substring("/launch/kill/".length()));
            return;
        }

        if ("GET".equalsIgnoreCase(method) && path.startsWith("/launch/status/")) {
            handleStatus(exchange, path.substring("/launch/status/".length()));
            return;
        }

        if ("GET".equalsIgnoreCase(method) && path.equals("/launch/instances")) {
            handleListInstances(exchange);
            return;
        }

        if ("GET".equalsIgnoreCase(method) && path.startsWith("/launch/instances/")) {
            handleGetInstance(exchange, path.substring("/launch/instances/".length()));
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

        if (req == null) { HttpUtils.badRequest(exchange, "Body nulo"); return; }

        String validationError = req.validate();
        if (validationError != null) { HttpUtils.badRequest(exchange, validationError); return; }

        String launchId = launcher.launch(req);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("launchId",       launchId);
        response.put("status",         "launching");
        response.put("version",        req.version);
        response.put("username",       req.resolvedUsername());
        response.put("instancePath",   req.resolvedInstancePath());
        response.put("runningInstances", launcher.getRunningCount());
        response.put("authlibInjector", req.isAuthlibEnabled()
                ? Map.of("enabled", true, "server", req.authlibInjector.serverUrl)
                : Map.of("enabled", false));
        response.put("message",  "Minecraft launching. Conectate al WS para logs en tiempo real.");
        response.put("kill",     "/launch/kill/"       + launchId);
        response.put("status",   "/launch/status/"     + launchId);
        response.put("details",  "/launch/instances/"  + launchId);

        HttpUtils.accepted(exchange, response);
    }

    private void handleKill(HttpExchange exchange, String launchId) throws IOException {
        if (!HttpUtils.requireMethod(exchange, "POST")) return;
        boolean killed = launcher.kill(launchId);
        if (killed) {
            HttpUtils.ok(exchange, Map.of("launchId", launchId, "status", "killed"));
        } else {
            HttpUtils.notFound(exchange, "Proceso no encontrado o ya terminado: " + launchId);
        }
    }

    private void handleStatus(HttpExchange exchange, String launchId) throws IOException {
        if (!HttpUtils.requireMethod(exchange, "GET")) return;
        boolean running = launcher.isRunning(launchId);
        Map<String, Object> data = launcher.getInstanceData(launchId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("launchId", launchId);
        resp.put("running",  running);
        resp.put("status",   running ? "running" : "stopped");
        if (data != null) resp.put("details", data);
        HttpUtils.ok(exchange, resp);
    }

    private void handleListInstances(HttpExchange exchange) throws IOException {
        if (!HttpUtils.requireMethod(exchange, "GET")) return;
        List<Map<String, Object>> instances = launcher.getAllInstances();
        HttpUtils.ok(exchange, Map.of(
                "count",     instances.size(),
                "running",   launcher.getRunningCount(),
                "instances", instances));
    }

    private void handleGetInstance(HttpExchange exchange, String launchId) throws IOException {
        if (!HttpUtils.requireMethod(exchange, "GET")) return;
        Map<String, Object> data = launcher.getInstanceData(launchId);
        if (data == null) {
            HttpUtils.notFound(exchange, "Instancia no encontrada: " + launchId);
            return;
        }
        HttpUtils.ok(exchange, data);
    }
}
