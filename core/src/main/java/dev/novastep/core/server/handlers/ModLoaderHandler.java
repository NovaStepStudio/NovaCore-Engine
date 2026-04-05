package dev.novastep.core.server.handlers;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.modloader.ModLoaderOrchestrator;
import dev.novastep.core.modloader.ModLoaderRegistry;
import dev.novastep.core.modloader.model.InstalledLoader;
import dev.novastep.core.modloader.model.LoaderVersion;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.server.ModLoaderRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModLoaderHandler implements HttpHandler {

    private static final String LOG  = "ModLoaderHandler";
    private static final Gson   GSON = new Gson();

    private final ModLoaderOrchestrator orchestrator;

    public ModLoaderHandler(ModLoaderOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equals(method) && path.equals("/modloaders")) {
                handleListLoaders(exchange);
            } else if ("GET".equals(method) && path.startsWith("/modloaders/versions/")) {
                handleGetVersions(exchange, path);
            } else if ("POST".equals(method) && path.equals("/modloaders/install")) {
                handleInstall(exchange);
            } else if ("GET".equals(method) && path.startsWith("/modloaders/state/")) {
                handleGetState(exchange, path);
            } else if ("DELETE".equals(method) && path.startsWith("/modloaders/state/")) {
                handleDeleteState(exchange, path);
            } else {
                HttpUtils.send(exchange, 404, Map.of("error", "Not found: " + path));
            }
        } catch (Exception ex) {
            CoreLogger.get().error(LOG, "Unhandled error: " + ex.getMessage(), ex);
            HttpUtils.send(exchange, 500, Map.of("error", ex.getMessage()));
        }
    }

    private void handleListLoaders(HttpExchange exchange) throws IOException {
        HttpUtils.send(exchange, 200, Map.of("loaders", ModLoaderRegistry.get().names()));
    }

    private void handleGetVersions(HttpExchange exchange, String path) throws IOException {
        String[] parts      = path.split("/");
        if (parts.length < 5) {
            HttpUtils.send(exchange, 400, Map.of("error", "Expected: /modloaders/versions/{loader}/{mcVersion}"));
            return;
        }
        String loaderName = parts[3];
        String mcVersion  = parts[4];

        try {
            List<LoaderVersion> versions = orchestrator.getVersions(loaderName, mcVersion);
            HttpUtils.send(exchange, 200, Map.of("versions", versions));
        } catch (IllegalArgumentException ex) {
            HttpUtils.send(exchange, 400, Map.of("error", ex.getMessage()));
        } catch (IOException | InterruptedException ex) {
            HttpUtils.send(exchange, 502, Map.of("error", "Upstream error: " + ex.getMessage()));
        }
    }

    private void handleInstall(HttpExchange exchange) throws IOException {
        ModLoaderRequest req = parseBody(exchange, ModLoaderRequest.class);
        if (req == null) return;

        String validationError = req.validate();
        if (validationError != null) {
            HttpUtils.send(exchange, 400, Map.of("error", validationError));
            return;
        }

        String sessionId = Long.toHexString(System.currentTimeMillis());

        Thread.ofVirtual().name("modloader-install-" + sessionId).start(() -> {
            try {
                orchestrator.install(
                        sessionId,
                        req.loader,
                        req.loaderVersion,
                        req.minecraftVersion,
                        Path.of(req.resolvedInstancePath()),
                        req.resolvedLibrariesPath(),
                        req.resolvedMinecraftJar());
            } catch (Exception ex) {
                CoreLogger.get().error(LOG, "[" + sessionId + "] Install failed: " + ex.getMessage(), ex);
            }
        });

        HttpUtils.send(exchange, 202, Map.of(
                "sessionId",  sessionId,
                "loader",     req.loader,
                "mcVersion",  req.minecraftVersion,
                "status",     "started"));
    }

    private void handleGetState(HttpExchange exchange, String path) throws IOException {
        String instancePath = extractInstancePath(path, "/modloaders/state/");
        Optional<InstalledLoader> state = orchestrator.loadState(Path.of(instancePath));
        if (state.isPresent()) {
            HttpUtils.send(exchange, 200, state.get());
        } else {
            HttpUtils.send(exchange, 404, Map.of("error", "No modloader installed in: " + instancePath));
        }
    }

    private void handleDeleteState(HttpExchange exchange, String path) throws IOException {
        String instancePath = extractInstancePath(path, "/modloaders/state/");
        try {
            orchestrator.removeState(Path.of(instancePath));
            HttpUtils.send(exchange, 200, Map.of("removed", true));
        } catch (IOException ex) {
            HttpUtils.send(exchange, 500, Map.of("error", ex.getMessage()));
        }
    }

    private String extractInstancePath(String path, String prefix) {
        return java.net.URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8);
    }

    private <T> T parseBody(HttpExchange exchange, Class<T> type) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                HttpUtils.send(exchange, 400, Map.of("error", "Request body is empty"));
                return null;
            }
            return GSON.fromJson(body, type);
        } catch (Exception ex) {
            HttpUtils.send(exchange, 400, Map.of("error", "Invalid JSON: " + ex.getMessage()));
            return null;
        }
    }
}
