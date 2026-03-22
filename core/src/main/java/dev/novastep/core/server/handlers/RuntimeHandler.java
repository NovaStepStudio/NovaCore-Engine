package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.minecraft.RuntimeDownloader;
import dev.novastep.core.minecraft.manifest.ManifestClient;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimeHandler implements HttpHandler {

    private final DownloadManager  downloadManager;
    private final EventBroadcaster broadcaster;
    private final ManifestClient   manifestClient;

    public RuntimeHandler(DownloadManager downloadManager, EventBroadcaster broadcaster) {
        this.downloadManager = downloadManager;
        this.broadcaster = broadcaster;
        this.manifestClient = new ManifestClient();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange)) return;
        if (!HttpUtils.requireMethod(exchange, "POST")) return;

        String body = HttpUtils.readBody(exchange);
        if (body == null || body.isBlank()) {
            HttpUtils.badRequest(exchange, "Body vacío");
            return;
        }

        com.google.gson.JsonObject req;
        try {
            req = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            HttpUtils.badRequest(exchange, "JSON inválido: " + e.getMessage());
            return;
        }

        if (!req.has("version") || !req.has("instancePath")) {
            HttpUtils.badRequest(exchange, "Se requieren 'version' e 'instancePath'");
            return;
        }

        String version      = req.get("version").getAsString().trim();
        String instancePath = req.get("instancePath").getAsString().trim();

        Thread.ofVirtual().name("runtime-dl").start(() -> {
            try {
                
                VersionInfo versionInfo = manifestClient.fetchVersionById(version);
                if (versionInfo.javaVersion == null) {
                    broadcaster.emit("runtime_error", Map.of(
                        "version", version,
                        "error", "Esta versión no especifica javaVersion"
                    ));
                    return;
                }

                String component = versionInfo.javaVersion.component;
                RuntimeDownloader rd = new RuntimeDownloader(downloadManager, broadcaster);

                String javaPath = rd.downloadRuntime(
                    "runtime-" + version,
                    component,
                    Path.of(instancePath).toAbsolutePath()
                );

                broadcaster.emit("runtime_ready", Map.of(
                    "version", version,
                    "component",component,
                    "javaPath", javaPath
                ));

            } catch (Exception ex) {
                broadcaster.emit("runtime_error", Map.of(
                    "version", version,
                    "error", ex.getMessage()
                ));
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "downloading");
        response.put("version", version);
        response.put("instancePath",instancePath);
        response.put("runtimeDir", instancePath + "/runtime/");
        response.put("message", "Runtime descargándose. Conectate al WS para ver progreso.");

        HttpUtils.accepted(exchange, response);
    }
}