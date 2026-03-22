package dev.novastep.core.minecraft;

import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.downloader.DownloadResult;
import dev.novastep.core.downloader.DownloadTask;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.manifest.ManifestClient;
import dev.novastep.core.minecraft.models.AssetIndexManifest;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.server.InstallRequest;
import dev.novastep.core.websocket.EventBroadcaster;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class InstallOrchestrator {

    private static final String LOG = "InstallOrchestrator";

    private final DownloadManager downloadManager;
    private final EventBroadcaster broadcaster;
    private final ManifestClient manifestClient;
    private final RuntimeDownloader runtimeDownloader;

    public InstallOrchestrator(DownloadManager downloadManager, EventBroadcaster broadcaster) {
        this.downloadManager   = downloadManager;
        this.broadcaster       = broadcaster;
        this.manifestClient    = new ManifestClient();
        this.runtimeDownloader = new RuntimeDownloader(downloadManager, broadcaster);
    }

    public String install(InstallRequest request) {
        String sessionId = downloadManager.createSession();

        CoreLogger.get().info(LOG, "Install requested: version=" + request.version + ", path=" + request.resolvedInstancePath());
        broadcaster.emitDebug(sessionId, "Install requested: version=" + request.version + ", path=" + request.resolvedInstancePath());

        Thread.ofVirtual().name("install-" + sessionId).start(() -> {
            try {
                runInstall(sessionId, request);
            } catch (Exception ex) {
                String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                CoreLogger.get().error(LOG, "Fatal error in session " + sessionId + ": " + msg, ex);
                downloadManager.getSession(sessionId).ifPresent(s -> s.markFailed(msg));
                broadcaster.emitSessionFailed(sessionId, msg);
            }
        });

        return sessionId;
    }

    private void runInstall(String sessionId, InstallRequest request) throws Exception {
        Path instancePath = Path.of(request.resolvedInstancePath());

        broadcaster.emit("install_step", Map.of("sessionId", sessionId, "step", "resolving_version", "version", request.version));

        VersionInfo versionInfo;
        try {
            versionInfo = manifestClient.fetchVersionById(request.version);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Version not found: " + e.getMessage(), e);
        }

        broadcaster.emitManifestResolved(sessionId, versionInfo.id);
        TaskBuilder.saveVersionJson(versionInfo, instancePath);

        CoreLogger.get().info(LOG, "[" + sessionId + "] Version resolved: " + versionInfo.id + ", mainClass=" + versionInfo.mainClass);

        AssetIndexManifest assetIndex = null;
        if (request.shouldDownloadAssets() && versionInfo.assetIndex != null) {
            broadcaster.emit("install_step", Map.of( "sessionId", sessionId, "step", "fetching_asset_index", "indexId", versionInfo.assetIndex.id));
            assetIndex = manifestClient.fetchAssetIndex(versionInfo.assetIndex);
            CoreLogger.get().info(LOG, "[" + sessionId + "] Asset index: " + assetIndex.totalCount() + " assets, " + formatBytes(assetIndex.totalBytes()));
        }

        if (request.shouldDownloadJvm() && versionInfo.javaVersion != null) {
            broadcaster.emit("install_step", Map.of(
                "sessionId", sessionId, "step", "downloading_jvm",
                "component", versionInfo.javaVersion.component,
                "major", versionInfo.javaVersion.majorVersion));
            try {
                String javaPath = runtimeDownloader.downloadRuntime(
                    sessionId, versionInfo.javaVersion.component, instancePath);
                CoreLogger.get().info(LOG, "[" + sessionId + "] JVM ready: " + javaPath);
                broadcaster.emitDebug(sessionId, "JVM listo en: " + javaPath);
            } catch (Exception ex) {
                CoreLogger.get().warn(LOG, "[" + sessionId + "] Could not download JVM: " + ex.getMessage());
                broadcaster.emitDebug(sessionId, "Advertencia: no se pudo descargar JVM: " + ex.getMessage());
            }
        }

        broadcaster.emit("install_step", Map.of("sessionId", sessionId, "step", "building_task_list"));
        TaskBuilder builder = TaskBuilder.fromRequest(request, broadcaster);
        List<DownloadTask> tasks = builder.build(sessionId, versionInfo, assetIndex, instancePath);

        broadcaster.emit("tasks_ready", Map.of(
            "sessionId", sessionId,
            "totalTasks",tasks.size(),
            "breakdown", countByCategory(tasks)));

        CoreLogger.get().info(LOG, "[" + sessionId + "] Tasks ready: " + tasks.size());

        if (tasks.isEmpty()) {
            downloadManager.getSession(sessionId).ifPresent(s -> s.markCompleted());
            broadcaster.emitSessionCompleted(sessionId, 0, 0);
            return;
        }

        broadcaster.emit("install_step", Map.of("sessionId", sessionId, "step", "downloading", "files", tasks.size()));
        CompletableFuture<List<DownloadResult>> future = downloadManager.submitAll(sessionId, tasks);
        List<DownloadResult> results = future.get();

        long succeeded = results.stream().filter(r -> r.success && !r.skipped).count();
        long skipped = results.stream().filter(r -> r.skipped).count();
        long failed = results.stream().filter(DownloadResult::isFailed).count();

        if (request.shouldDownloadNatives() && failed == 0) {
            broadcaster.emit("install_step", Map.of("sessionId", sessionId, "step", "extracting_natives"));
            try {
                ClasspathBuilder cpBuilder = new ClasspathBuilder(
                    versionInfo, instancePath,
                    request.resolvedLibrariesPath().toAbsolutePath(),
                    request.resolvedAssetsPath().toAbsolutePath());
                Path nativesDir = cpBuilder.extractNatives();
                CoreLogger.get().info(LOG, "[" + sessionId + "] Natives extracted: " + nativesDir);
                broadcaster.emitDebug(sessionId, "Nativos extraídos en: " + nativesDir);
            } catch (Exception ex) {
                CoreLogger.get().warn(LOG, "[" + sessionId + "] Natives extraction warning: " + ex.getMessage());
                broadcaster.emitDebug(sessionId, "Advertencia al extraer nativos: " + ex.getMessage());
            }
        }

        String summary = String.format("Install finished: %d downloaded, %d skipped, %d failed", succeeded, skipped, failed);
        CoreLogger.get().info(LOG, "[" + sessionId + "] " + summary);
        broadcaster.emitDebug(sessionId, summary);

        if (request.isDebug() && failed > 0) {
            results.stream()
                .filter(DownloadResult::isFailed)
                .forEach(r -> {
                    CoreLogger.get().error(LOG, "FAILED: " + r.task.name + " → " + r.error);
                    broadcaster.emitDebug(sessionId, "FAILED: " + r.task.name + " → " + r.error);
                });
        }
    }

    private static Map<String, Long> countByCategory(List<DownloadTask> tasks) {
        return Map.of(
            "client", tasks.stream().filter(t -> "client".equals(t.category)).count(),
            "libraries", tasks.stream().filter(t -> "library".equals(t.category)).count(),
            "assets", tasks.stream().filter(t -> "asset".equals(t.category)).count(),
            "natives", tasks.stream().filter(t -> "native".equals(t.category)).count(),
            "asset_index", tasks.stream().filter(t -> "asset_index".equals(t.category)).count()
        );
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024)               return bytes + " B";
        if (bytes < 1024 * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
