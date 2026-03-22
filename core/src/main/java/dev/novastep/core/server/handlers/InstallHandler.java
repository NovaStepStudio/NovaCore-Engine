package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.minecraft.InstallOrchestrator;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.server.InstallRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class InstallHandler implements HttpHandler {

    private final InstallOrchestrator orchestrator;

    public InstallHandler(InstallOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange)) return;
        if (!HttpUtils.requireMethod(exchange, "POST")) return;

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
        response.put("sessionId", sessionId);
        response.put("version", request.version);
        response.put("instancePath", request.resolvedInstancePath());
        response.put("status", "started");
        response.put("progress", "/progress?sessionId=" + sessionId);
        response.put("websocket", "Conectate al WS para eventos en tiempo real");

        HttpUtils.accepted(exchange, response);
    }
}