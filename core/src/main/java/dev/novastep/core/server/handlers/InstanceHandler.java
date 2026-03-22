package dev.novastep.core.server.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.InstallOrchestrator;
import dev.novastep.core.minecraft.InstanceManager;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.server.InstallRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class InstanceHandler implements HttpHandler {

    private static final String LOG = "InstanceHandler";

    private final InstanceManager    instanceManager;
    private final InstallOrchestrator orchestrator;
    private final String             instancesRootPath;

    public InstanceHandler(InstanceManager instanceManager,
                           InstallOrchestrator orchestrator,
                           String instancesRootPath) {
        this.instanceManager   = instanceManager;
        this.orchestrator      = orchestrator;
        this.instancesRootPath = instancesRootPath;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange)) return;

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method) && path.equals("/instances")) {
            handleList(exchange); return;
        }
        if ("POST".equalsIgnoreCase(method) && path.equals("/instances")) {
            handleCreate(exchange); return;
        }
        if (path.startsWith("/instances/")) {
            String rest   = path.substring("/instances/".length());
            String[] parts = rest.split("/", 2);
            String id  = parts[0];
            String sub = parts.length > 1 ? parts[1] : "";

            if ("path".equals(sub) && "GET".equalsIgnoreCase(method)) {
                handleGetPath(exchange, id); return;
            }
            if (sub.isEmpty()) {
                switch (method.toUpperCase()) {
                    case "GET" -> handleGet(exchange, id);
                    case "PATCH" -> handleUpdate(exchange, id);
                    case "DELETE" -> handleDelete(exchange, id);
                    default -> HttpUtils.methodNotAllowed(exchange);
                }
                return;
            }
        }
        HttpUtils.notFound(exchange, "Unknown instances endpoint: " + path);
    }

    private void handleList(HttpExchange ex) throws IOException {
        try {
            var list = instanceManager.listAll();
            HttpUtils.ok(ex, Map.of("count", list.size(), "instances", list));
        } catch (Exception e) {
            CoreLogger.get().error(LOG, "listAll failed: " + e.getMessage(), e);
            HttpUtils.serverError(ex, e.getMessage());
        }
    }

    private void handleCreate(HttpExchange ex) throws IOException {
        String body = HttpUtils.readBody(ex);
        if (body == null || body.isBlank()) { HttpUtils.badRequest(ex, "Body vacío"); return; }
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            if (!req.has("name") || !req.has("mcVersion")) {
                HttpUtils.badRequest(ex, "Se requieren 'name' y 'mcVersion'"); return;
            }
            String name      = req.get("name").getAsString().trim();
            String mcVersion = req.get("mcVersion").getAsString().trim();

            InstanceManager.InstanceMeta overrides = null;
            if (req.has("config")) {
                overrides = HttpUtils.GSON.fromJson(req.get("config"), InstanceManager.InstanceMeta.class);
            }

            var meta        = instanceManager.create(name, mcVersion, overrides);
            String instPath = instanceManager.getInstancePath(meta.id);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id",   meta.id);
            response.put("name", meta.name);
            response.put("path", instPath);

            boolean autoInstall = req.has("autoInstall") && req.get("autoInstall").getAsBoolean();
            if (autoInstall && req.has("install")) {
                InstallRequest installReq = HttpUtils.GSON.fromJson(req.get("install"), InstallRequest.class);
                installReq.version      = mcVersion;
                installReq.instancePath = instPath;

                if (req.get("install").getAsJsonObject().has("sharedPath")) {
                    installReq.sharedPath = req.get("install").getAsJsonObject().get("sharedPath").getAsString();
                }

                String sessionId = orchestrator.install(installReq);
                response.put("installSessionId", sessionId);
                response.put("installStatus", "started");
                response.put("installProgress", "/progress?sessionId=" + sessionId);
                CoreLogger.get().info(LOG, "Auto-install started for instance: " + meta.id + " session=" + sessionId);
            }

            HttpUtils.ok(ex, response);
        } catch (Exception e) {
            CoreLogger.get().error(LOG, "create failed: " + e.getMessage(), e);
            HttpUtils.serverError(ex, e.getMessage());
        }
    }

    private void handleGet(HttpExchange ex, String id) throws IOException {
        try {
            var info = instanceManager.get(id);
            if (info == null) { HttpUtils.notFound(ex, "Instance not found: " + id); return; }
            HttpUtils.ok(ex, info);
        } catch (Exception e) {
            HttpUtils.serverError(ex, e.getMessage());
        }
    }

    private void handleUpdate(HttpExchange ex, String id) throws IOException {
        String body = HttpUtils.readBody(ex);
        if (body == null || body.isBlank()) { HttpUtils.badRequest(ex, "Body vacío"); return; }
        try {
            InstanceManager.InstanceMeta updates = HttpUtils.GSON.fromJson(body, InstanceManager.InstanceMeta.class);
            var updated = instanceManager.update(id, updates);
            HttpUtils.ok(ex, Map.of("updated", true, "id", updated.id));
        } catch (IllegalArgumentException e) {
            HttpUtils.notFound(ex, e.getMessage());
        } catch (Exception e) {
            CoreLogger.get().error(LOG, "update failed: " + e.getMessage(), e);
            HttpUtils.serverError(ex, e.getMessage());
        }
    }

    private void handleDelete(HttpExchange ex, String id) throws IOException {
        try {
            boolean deleted = instanceManager.delete(id);
            if (!deleted) { HttpUtils.notFound(ex, "Instance not found: " + id); return; }
            HttpUtils.ok(ex, Map.of("deleted", true, "id", id));
        } catch (Exception e) {
            CoreLogger.get().error(LOG, "delete failed: " + e.getMessage(), e);
            HttpUtils.serverError(ex, e.getMessage());
        }
    }

    private void handleGetPath(HttpExchange ex, String id) throws IOException {
        try {
            String path = instanceManager.getInstancePath(id);
            HttpUtils.ok(ex, Map.of("id", id, "path", path));
        } catch (IllegalArgumentException e) {
            HttpUtils.notFound(ex, e.getMessage());
        } catch (Exception e) {
            HttpUtils.serverError(ex, e.getMessage());
        }
    }
}
