package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.minecraft.RuntimeDownloader;
import dev.novastep.core.minecraft.manifest.ManifestClient;
import dev.novastep.core.minecraft.version.VersionInfo;
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
        this.broadcaster     = broadcaster;
        this.manifestClient  = new ManifestClient();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange)) return;
        if (!HttpUtils.requireMethod(exchange, "POST")) return;

        String body = HttpUtils.readBody(exchange);
        if (body == null || body.isBlank()) { HttpUtils.badRequest(exchange, "Empty body"); return; }

        com.google.gson.JsonObject req;
        try {
            req = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            HttpUtils.badRequest(exchange, "Invalid JSON: " + e.getMessage());
            return;
        }

        if (!req.has("version") || !req.has("instancePath")) {
            HttpUtils.badRequest(exchange, "'version' and 'instancePath' are required");
            return;
        }

        String version      = req.get("version").getAsString().trim();
        String instancePath = req.get("instancePath").getAsString().trim();
        String sharedPath   = (req.has("sharedPath") && !req.get("sharedPath").isJsonNull())
                ? req.get("sharedPath").getAsString().trim() : null;

        boolean useShared = sharedPath != null && !sharedPath.isBlank();

        String runtimeDir = useShared
                ? Path.of(sharedPath).toAbsolutePath().resolve("java").toString()
                : Path.of(instancePath).toAbsolutePath().resolve("runtime").toString();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",       "downloading");
        response.put("version",      version);
        response.put("instancePath", instancePath);
        response.put("runtimeDir",   runtimeDir + "/");
        response.put("shared",       useShared);
        response.put("message",      "Runtime download started. Connect to WS for progress events.");
        HttpUtils.accepted(exchange, response);

        final String finalSharedPath   = useShared ? sharedPath : null;
        final Path   finalInstancePath = Path.of(instancePath).toAbsolutePath();

        Thread.ofVirtual().name("runtime-dl-" + version).start(() -> {
            try {
                VersionInfo versionInfo = manifestClient.fetchVersionById(version);
                if (versionInfo.javaVersion == null) {
                    broadcaster.emit("runtime_error", Map.of(
                            "version", version,
                            "error",   "This version does not specify a javaVersion"));
                    return;
                }

                String component = versionInfo.javaVersion.component;
                RuntimeDownloader rd = new RuntimeDownloader(downloadManager, broadcaster);

                Path sharedPathObj = finalSharedPath != null
                        ? Path.of(finalSharedPath).toAbsolutePath() : null;

                String javaPath = rd.downloadRuntime(
                        "runtime-" + version,
                        component,
                        finalInstancePath,
                        sharedPathObj);

                broadcaster.emit("runtime_ready", Map.of(
                        "version",   version,
                        "component", component,
                        "javaPath",  javaPath,
                        "shared",    useShared));

            } catch (Exception ex) {
                broadcaster.emit("runtime_error", Map.of(
                        "version", version,
                        "error",   ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
        });
    }
}
