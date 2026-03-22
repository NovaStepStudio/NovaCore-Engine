package dev.novastep.core;

import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.server.CoreHttpServer;
import dev.novastep.core.websocket.EventBroadcaster;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        int httpPort = 7878;
        int wsPort = 7879;
        int maxThreads = 32;
        String instancesDir = null;
        String logDir = null;
        String launcherName = "novacore-engine";
        String logLevel = "INFO";

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--port" -> httpPort = Integer.parseInt(args[i + 1]);
                case "--ws-port" -> wsPort = Integer.parseInt(args[i + 1]);
                case "--threads" -> maxThreads = Integer.parseInt(args[i + 1]);
                case "--instances-dir" -> instancesDir= args[i + 1];
                case "--log-dir" -> logDir = args[i + 1];
                case "--launcher-name" -> launcherName = args[i + 1];
                case "--log-level" -> logLevel = args[i + 1];
            }
        }

        Path instancesPath = instancesDir != null
            ? Path.of(instancesDir).toAbsolutePath()
            : Path.of(System.getProperty("user.dir")).resolve("instances").toAbsolutePath();

        Path logDirPath = logDir != null
            ? Path.of(logDir).toAbsolutePath()
            : instancesPath.getParent().resolve("logs").toAbsolutePath();

        CoreLogger.Level level;
        try { level = CoreLogger.Level.valueOf(logLevel.toUpperCase()); }
        catch (IllegalArgumentException e) { level = CoreLogger.Level.INFO; }

        CoreLogger.init(launcherName, logDirPath, level);
        CoreLogger log = CoreLogger.get();

        log.info("Core", "novacore-engine starting up");
        log.info("Core", "Instances dir: " + instancesPath);
        log.info("Core", "Log file: " + log.getLogFile());

        EventBroadcaster broadcaster = new EventBroadcaster(wsPort);
        broadcaster.start();

        DownloadManager downloadManager = new DownloadManager(maxThreads, broadcaster);

        CoreHttpServer httpServer = new CoreHttpServer(
            httpPort, downloadManager, broadcaster, instancesPath.toString()
        );
        httpServer.start();

        log.info("Core", "HTTP > http://localhost:" + httpPort);
        log.info("Core", "WS > ws://localhost:" + wsPort);
        log.info("Core", "Threads > " + maxThreads);
        log.info("Core", "Ready");

        System.out.println("[Core] HTTP > http://localhost:" + httpPort);
        System.out.println("[Core] WS > ws://localhost:" + wsPort);
        System.out.println("[Core] Ready");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Core", "Shutting down...");
            httpServer.stop();
            downloadManager.shutdown();
            try { broadcaster.stop(2000); } catch (Exception ignored) {}
            log.info("Core", "Stopped.");
            log.close();
        }, "shutdown-hook"));

        Thread.currentThread().join();
    }
}
