package dev.novastep.core.server;

import com.sun.net.httpserver.HttpServer;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.InstallOrchestrator;
import dev.novastep.core.minecraft.InstanceManager;
import dev.novastep.core.minecraft.MinecraftLauncher;
import dev.novastep.core.minecraft.manifest.ManifestClient;
import dev.novastep.core.server.handlers.*;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

public class CoreHttpServer {

    private static final String LOG = "CoreHttpServer";

    private final HttpServer          httpServer;
    private final InstallOrchestrator orchestrator;
    private final ManifestClient      manifestClient;
    private final MinecraftLauncher   launcher;
    private final InstanceManager     instanceManager;
    private final EventBroadcaster    broadcaster;

    public CoreHttpServer(int port, DownloadManager downloadManager, EventBroadcaster broadcaster, String instancesDir) throws IOException {
        this.broadcaster = broadcaster;
        this.orchestrator = new InstallOrchestrator(downloadManager, broadcaster);
        this.manifestClient = new ManifestClient();
        this.launcher = new MinecraftLauncher(broadcaster);
        this.instanceManager = new InstanceManager(Path.of(instancesDir).toAbsolutePath());

        httpServer = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        registerRoutes(downloadManager, instancesDir);
    }

    private void registerRoutes(DownloadManager downloadManager, String instancesDir) {
        httpServer.createContext("/api", new ApiHandler());
        httpServer.createContext("/versions", new VersionsHandler(manifestClient));
        httpServer.createContext("/install", new InstallHandler(orchestrator));
        httpServer.createContext("/progress", new ProgressHandler(downloadManager));

        httpServer.createContext("/instances",
            new InstanceHandler(instanceManager, orchestrator, instancesDir));

        httpServer.createContext("/system/resources", new SystemResourcesHandler());

        httpServer.createContext("/runtime",
            new RuntimeHandler(downloadManager, broadcaster));

        httpServer.createContext("/launch",
            new LaunchHandler(launcher));

        httpServer.createContext("/debug/download/client",
            new DebugHandler(downloadManager, "client"));
        httpServer.createContext("/debug/download/libraries",
            new DebugHandler(downloadManager, "libraries"));
        httpServer.createContext("/debug/download/assets",
            new DebugHandler(downloadManager, "assets"));
        httpServer.createContext("/debug/download/natives",
            new DebugHandler(downloadManager, "natives"));

        httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                exchange.getResponseHeaders().set("Location", "/api");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            HttpUtils.sendJson(exchange, 404, Map.of(
                "error",  "Endpoint not found",
                "path",   path,
                "status", 404,
                "hint",   "See GET /api for available endpoints"
            ));
        });

        CoreLogger.get().info(LOG, "Routes registered");
    }

    public void start() {
        httpServer.start();
        CoreLogger.get().info(LOG, "HTTP server started on port " + httpServer.getAddress().getPort());
    }

    public void stop(int delaySeconds) {
        httpServer.stop(delaySeconds);
        CoreLogger.get().info(LOG, "HTTP server stopped");
    }

    public void stop() { httpServer.stop(0); }

    public int getPort() { return httpServer.getAddress().getPort(); }
}
