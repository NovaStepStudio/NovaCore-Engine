package dev.novastep.core.server;


import dev.novastep.core.modloader.ModLoaderOrchestrator;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.Executors;

public class CoreHttpServer {

    private static final String LOG = "CoreHttpServer";

    private final HttpServer       httpServer;
    private final InstallOrchestrator orchestrator;
    private final ManifestClient   manifestClient;
    private final MinecraftLauncher launcher;
    private final InstanceManager  instanceManager;
    private final EventBroadcaster broadcaster;
    private final byte[]           tokenBytes;

    public CoreHttpServer(int port, DownloadManager downloadManager,
            EventBroadcaster broadcaster, String instancesDir,
            String accessToken) throws IOException {
        this.broadcaster     = broadcaster;
        this.tokenBytes      = accessToken.getBytes(StandardCharsets.UTF_8);
        this.orchestrator    = new InstallOrchestrator(downloadManager, broadcaster);
        this.manifestClient  = new ManifestClient();
        ModLoaderOrchestrator modLoaderOrchestrator = new ModLoaderOrchestrator(downloadManager, broadcaster);
        this.launcher = new MinecraftLauncher(broadcaster, modLoaderOrchestrator);
        this.instanceManager = new InstanceManager(Path.of(instancesDir).toAbsolutePath());

        httpServer = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        registerRoutes(downloadManager);
    }

    private void registerRoutes(DownloadManager downloadManager) {
        secure(httpServer.createContext("/api",                          new ApiHandler()));
        secure(httpServer.createContext("/versions",                     new VersionsHandler(manifestClient)));
        secure(httpServer.createContext("/install",                      new InstallHandler(orchestrator)));
        secure(httpServer.createContext("/progress",                     new ProgressHandler(downloadManager)));
        secure(httpServer.createContext("/instances",                    new InstanceHandler(instanceManager, orchestrator)));
        secure(httpServer.createContext("/system/resources",             new SystemResourcesHandler()));
        secure(httpServer.createContext("/runtime",                      new RuntimeHandler(downloadManager, broadcaster)));
        secure(httpServer.createContext("/launch",                       new LaunchHandler(launcher)));
        secure(httpServer.createContext("/debug/download/client",        new DebugHandler(downloadManager, "client")));
        secure(httpServer.createContext("/debug/download/libraries",     new DebugHandler(downloadManager, "libraries")));
        secure(httpServer.createContext("/debug/download/assets",        new DebugHandler(downloadManager, "assets")));
        secure(httpServer.createContext("/debug/download/natives",       new DebugHandler(downloadManager, "natives")));
        secure(httpServer.createContext("/modloaders",                   new ModLoaderHandler(new ModLoaderOrchestrator(downloadManager, broadcaster))));
        
        secure(httpServer.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                exchange.getResponseHeaders().set("Location", "/api");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
            HttpUtils.notFound(exchange, "Endpoint not found — see GET /api for available endpoints");
        }));

        CoreLogger.get().info(LOG, "Routes registered (all protected by access token)");
    }

    private void secure(HttpContext ctx) {
        ctx.getFilters().add(new AuthFilter(tokenBytes));
    }

    private static final class AuthFilter extends Filter {
        private final byte[] tokenBytes;

        AuthFilter(byte[] tokenBytes) {
            this.tokenBytes = tokenBytes;
        }

        @Override
        public String description() { return "NovaCore Access Token Auth"; }

        @Override
        public void doFilter(HttpExchange ex, Chain chain) throws IOException {
            if (HttpUtils.handleCors(ex)) return;

            String header = ex.getRequestHeaders().getFirst("X-Access-Token");
            if (header == null || header.isBlank()
                    || !MessageDigest.isEqual(
                            tokenBytes,
                            header.getBytes(StandardCharsets.UTF_8))) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            chain.doFilter(ex);
        }
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
