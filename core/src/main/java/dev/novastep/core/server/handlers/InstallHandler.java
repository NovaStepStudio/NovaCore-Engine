package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.minecraft.InstallOrchestrator;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.server.request.InstallRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InstallHandler implements HttpHandler {

    private final InstallOrchestrator orchestrator;
    private final DownloadManager     downloadManager;

    public InstallHandler(InstallOrchestrator orchestrator, DownloadManager downloadManager) {
        this.orchestrator    = orchestrator;
        this.downloadManager = downloadManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange)) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method) && path.equals("/install")) {
            handleInstall(exchange);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && path.startsWith("/install/pause/")) {
            String sessionId = path.substring("/install/pause/".length());
            handleControl(exchange, "pause", sessionId);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && path.startsWith("/install/resume/")) {
            String sessionId = path.substring("/install/resume/".length());
            handleControl(exchange, "resume", sessionId);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && path.startsWith("/install/cancel/")) {
            String sessionId = path.substring("/install/cancel/".length());
            handleControl(exchange, "cancel", sessionId);
            return;
        }

        if ("GET".equalsIgnoreCase(method) && path.equals("/install/recovery")) {
            handleRecovery(exchange);
            return;
        }

        HttpUtils.notFound(exchange, "Unknown install endpoint: " + path);
    }

    private void handleInstall(HttpExchange exchange) throws IOException {
        String body = HttpUtils.readBody(exchange);
        if (body == null || body.isBlank()) {
            HttpUtils.badRequest(exchange, "Request body is empty");
            return;
        }

        InstallRequest request;
        try {
            request = HttpUtils.GSON.fromJson(body, InstallRequest.class);
        } catch (Exception e) {
            HttpUtils.badRequest(exchange, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (request == null) {
            HttpUtils.badRequest(exchange, "Request body cannot be null");
            return;
        }

        String validationError = request.validate();
        if (validationError != null) {
            HttpUtils.badRequest(exchange, validationError);
            return;
        }

        String sessionId;
        try {
            sessionId = orchestrator.install(request);
        } catch (Exception e) {
            HttpUtils.serverError(exchange, "Failed to start install: " + e.getMessage());
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId",    sessionId);
        response.put("version",      request.version);
        response.put("instancePath", request.resolvedInstancePath());
        response.put("status",       "started");
        response.put("progress",     "/progress?sessionId=" + sessionId);
        response.put("pause",        "/install/pause/"  + sessionId);
        response.put("resume",       "/install/resume/" + sessionId);
        response.put("cancel",       "/install/cancel/" + sessionId);
        response.put("websocket",    "Conectate al WS para eventos en tiempo real");

        HttpUtils.accepted(exchange, response);
    }

    private void handleControl(HttpExchange exchange, String action, String sessionId) throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            HttpUtils.badRequest(exchange, "sessionId requerido");
            return;
        }

        boolean ok = switch (action) {
            case "pause"  -> downloadManager.pauseSession(sessionId);
            case "resume" -> downloadManager.resumeSession(sessionId);
            case "cancel" -> downloadManager.cancelSession(sessionId);
            default       -> false;
        };

        if (!ok) {
            HttpUtils.notFound(exchange,
                    "Session not found or state incompatible: " + sessionId + " [" + action + "]");
            return;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", sessionId);
        response.put("action",    action);
        response.put("status",    "ok");
        HttpUtils.ok(exchange, response);
    }

    private void handleRecovery(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> snapshots = downloadManager.getRecoverySnapshots();
        HttpUtils.ok(exchange, Map.of(
                "count",     snapshots.size(),
                "snapshots", snapshots
        ));
    }
}
