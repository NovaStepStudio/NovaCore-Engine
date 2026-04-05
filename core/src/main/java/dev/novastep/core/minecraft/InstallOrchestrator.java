package dev.novastep.core.minecraft;

import com.google.gson.Gson;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.downloader.DownloadResult;
import dev.novastep.core.downloader.DownloadTask;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.manifest.ManifestClient;
import dev.novastep.core.minecraft.manifest.VersionMerger;
import dev.novastep.core.minecraft.models.AssetIndexManifest;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.modloader.ModLoaderOrchestrator;
import dev.novastep.core.modloader.model.InstalledLoader;
import dev.novastep.core.server.InstallRequest;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class InstallOrchestrator {

    private static final String LOG  = "InstallOrchestrator";
    private static final Gson   GSON = new Gson();

    private final DownloadManager       downloadManager;
    private final EventBroadcaster      broadcaster;
    private final ManifestClient        manifestClient;
    private final RuntimeDownloader     runtimeDownloader;
    private final ModLoaderOrchestrator modLoaderOrchestrator;

    public InstallOrchestrator(DownloadManager downloadManager, EventBroadcaster broadcaster) {
        this.downloadManager       = downloadManager;
        this.broadcaster           = broadcaster;
        this.manifestClient        = new ManifestClient();
        this.runtimeDownloader     = new RuntimeDownloader(downloadManager, broadcaster);
        this.modLoaderOrchestrator = new ModLoaderOrchestrator(downloadManager, broadcaster);
    }

    public String install(InstallRequest request) {
        String sessionId = downloadManager.createSession();

        CoreLogger.get().info(LOG, "Install: version=" + request.version
                + ", path=" + request.resolvedInstancePath()
                + (request.modloader != null ? ", modloader=" + request.modloader : ""));

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

        broadcaster.emit("install_step", Map.of(
                "sessionId", sessionId, "step", "resolving_version", "version", request.version));

        VersionInfo versionInfo;
        boolean     isOnline;

        try {
            versionInfo = manifestClient.fetchVersionWithInheritance(request.version);
            isOnline    = true;
            broadcaster.emitManifestResolved(sessionId, versionInfo.id);
            TaskBuilder.saveVersionJson(versionInfo, instancePath);
        } catch (IOException | InterruptedException networkEx) {
            CoreLogger.get().warn(LOG, "[" + sessionId + "] Network unavailable: "
                    + networkEx.getMessage() + " — looking for local cache for " + request.version);

            versionInfo = loadLocalVersionInfo(request);
            isOnline    = false;

            if (versionInfo == null) {
                throw new RuntimeException(
                        "No network and no local cache for version '" + request.version
                        + "'. Install at least once with internet access.");
            }

            broadcaster.emit("offline_mode", Map.of(
                    "sessionId", sessionId,
                    "version",   request.version,
                    "reason",    networkEx.getMessage()));
        }

        CoreLogger.get().info(LOG, "[" + sessionId + "] Version: " + versionInfo.id
                + " mainClass=" + versionInfo.mainClass
                + " [" + (isOnline ? "online" : "OFFLINE") + "]");

        AssetIndexManifest assetIndex = null;
        if (isOnline && request.shouldDownloadAssets() && versionInfo.assetIndex != null) {
            broadcaster.emit("install_step", Map.of(
                    "sessionId", sessionId, "step", "fetching_asset_index",
                    "indexId",   versionInfo.assetIndex.id));
            assetIndex = manifestClient.fetchAssetIndex(versionInfo.assetIndex);
        }

        if (isOnline && request.shouldDownloadJvm() && versionInfo.javaVersion != null) {
            broadcaster.emit("install_step", Map.of(
                    "sessionId", sessionId, "step",      "downloading_jvm",
                    "component", versionInfo.javaVersion.component,
                    "major",     versionInfo.javaVersion.majorVersion));
            try {
                Path sharedPath = request.hasSharedPath()
                        ? Path.of(request.sharedPath).toAbsolutePath() : null;
                String javaPath = runtimeDownloader.downloadRuntime(
                        sessionId, versionInfo.javaVersion.component, instancePath, sharedPath);
                CoreLogger.get().info(LOG, "[" + sessionId + "] JVM ready: " + javaPath);
            } catch (Exception ex) {
                CoreLogger.get().warn(LOG, "[" + sessionId + "] JVM download failed: " + ex.getMessage());
            }
        }

        broadcaster.emit("install_step", Map.of("sessionId", sessionId, "step", "building_task_list"));

        InstallRequest effectiveRequest = isOnline ? request : withNoDownloads(request);
        TaskBuilder    builder          = TaskBuilder.fromRequest(effectiveRequest, broadcaster);
        List<DownloadTask> tasks        = builder.build(sessionId, versionInfo, assetIndex, instancePath);

        broadcaster.emit("tasks_ready", Map.of(
                "sessionId",  sessionId,
                "totalTasks", tasks.size(),
                "offline",    !isOnline,
                "breakdown",  countByCategory(tasks)));

            if (!tasks.isEmpty()) {
                broadcaster.emit("install_step", Map.of(
                        "sessionId", sessionId, "step", "downloading", "files", tasks.size()));

                CompletableFuture<List<DownloadResult>> future = downloadManager.submitAll(sessionId, tasks);
                List<DownloadResult> results = future.get();

                long succeeded = results.stream().filter(r ->  r.success && !r.skipped).count();
                long skipped   = results.stream().filter(r ->  r.skipped).count();
                long failed    = results.stream().filter(DownloadResult::isFailed).count();

                if (request.shouldDownloadNatives() && failed == 0) {
                broadcaster.emit("install_step", Map.of("sessionId", sessionId, "step", "extracting_natives"));
                try {
                    String vanillaVersionId = resolveVanillaVersionId(request.version, instancePath);

                    ClasspathBuilder cpBuilder = new ClasspathBuilder(
                            versionInfo, instancePath,
                            effectiveRequest.resolvedLibrariesPath().toAbsolutePath(),
                            effectiveRequest.resolvedAssetsPath().toAbsolutePath(),
                            vanillaVersionId);

                    Path nativesDir = cpBuilder.extractNatives();
                    CoreLogger.get().info(LOG, "[" + sessionId + "] Natives extraídas en: " + nativesDir
                            + " (base vanilla: " + vanillaVersionId + ")");

                } catch (Exception ex) {
                    CoreLogger.get().warn(LOG, "[" + sessionId + "] Natives warning: " + ex.getMessage());
                }
            }

            CoreLogger.get().info(LOG, "[" + sessionId + "] Vanilla install done: "
                    + succeeded + " downloaded, " + skipped + " skipped, " + failed + " failed.");
        }

        if (isOnline && request.modloader != null && !request.modloader.isBlank()) {
            broadcaster.emit("install_step", Map.of(
                    "sessionId", sessionId, "step", "modloader", "loader", request.modloader));

            Path minecraftJar = instancePath
                    .resolve("versions").resolve(request.version)
                    .resolve(request.version + ".jar");

            modLoaderOrchestrator.install(
                    sessionId,
                    request.modloader,
                    request.modloaderVersion,
                    request.version,
                    instancePath,
                    effectiveRequest.resolvedLibrariesPath().toAbsolutePath(),
                    minecraftJar);

            try {
                repairModloaderLibraries(sessionId, instancePath, effectiveRequest);
            } catch (Exception ex) {
                CoreLogger.get().warn(LOG, "[" + sessionId + "] Modloader library repair: " + ex.getMessage());
            }
        }

        downloadManager.getSession(sessionId).ifPresent(s -> s.markCompleted());
        broadcaster.emitSessionCompleted(sessionId, 0, 0);
    }


    private void repairModloaderLibraries(String sessionId, Path instancePath, InstallRequest request) {
        Optional<InstalledLoader> stateOpt = modLoaderOrchestrator.loadState(instancePath);
        if (stateOpt.isEmpty()) return;

        String versionJsonId = stateOpt.get().versionJsonId;
        if (versionJsonId == null || versionJsonId.isBlank()) return;

        Path versionFile = instancePath.resolve("versions")
                .resolve(versionJsonId).resolve(versionJsonId + ".json");
        if (!Files.exists(versionFile)) return;

        VersionInfo loaderInfo;
        try {
            String raw = Files.readString(versionFile, StandardCharsets.UTF_8);
            loaderInfo = GSON.fromJson(raw, VersionInfo.class);
            if (loaderInfo.inheritsFrom != null && !loaderInfo.inheritsFrom.isBlank()) {
                Path parentFile = instancePath.resolve("versions")
                        .resolve(loaderInfo.inheritsFrom).resolve(loaderInfo.inheritsFrom + ".json");
                if (Files.exists(parentFile)) {
                    String parentRaw = Files.readString(parentFile, StandardCharsets.UTF_8);
                    VersionInfo parent = GSON.fromJson(parentRaw, VersionInfo.class);
                    loaderInfo = VersionMerger.merge(parent, loaderInfo);
                }
            }
        } catch (Exception e) {
            CoreLogger.get().warn(LOG, "[" + sessionId + "] Modloader version JSON read: " + e.getMessage());
            return;
        }

        if (loaderInfo.libraries == null) return;

        Path librariesPath = request.resolvedLibrariesPath().toAbsolutePath();
        List<DownloadTask> tasks = new ArrayList<>();

        for (VersionInfo.Library lib : loaderInfo.libraries) {
            if (!lib.isAllowed() || TaskBuilder.isNativeLib(lib)) continue;
            if (lib.downloads == null || lib.downloads.artifact == null) continue;
            VersionInfo.Artifact art = lib.downloads.artifact;
            if (art.path == null || art.url == null || art.url.isBlank()) continue;
            Path dest = librariesPath.resolve(art.path);
            if (Files.exists(dest)) continue;
            tasks.add(DownloadTask.library(sessionId, art.path, art.url, dest, art.size, art.sha1));
        }

        if (tasks.isEmpty()) {
            CoreLogger.get().info(LOG, "[" + sessionId + "] Modloader runtime libs: all present.");
            return;
        }

        CoreLogger.get().info(LOG, "[" + sessionId + "] Modloader runtime libs missing: " + tasks.size() + " — downloading...");
        try {
            List<DownloadResult> results = downloadManager.submitAll(sessionId, tasks).get();
            long ok   = results.stream().filter(r -> r.success && !r.skipped).count();
            long skip = results.stream().filter(r -> r.skipped).count();
            long fail = results.stream().filter(DownloadResult::isFailed).count();
            CoreLogger.get().info(LOG, "[" + sessionId + "] Modloader runtime libs: "
                    + ok + " downloaded, " + skip + " skipped, " + fail + " failed.");
        } catch (Exception e) {
            CoreLogger.get().warn(LOG, "[" + sessionId + "] Modloader runtime lib download: " + e.getMessage());
        }
    }

    private String resolveVanillaVersionId(String versionId, Path instancePath) {
        Path versionFile = instancePath.resolve("versions")
                .resolve(versionId).resolve(versionId + ".json");
        if (!Files.exists(versionFile)) return versionId;
        try {
            VersionInfo raw = GSON.fromJson(
                    Files.readString(versionFile, java.nio.charset.StandardCharsets.UTF_8),
                    VersionInfo.class);
            if (raw.inheritsFrom != null && !raw.inheritsFrom.isBlank()) {
                return resolveVanillaVersionId(raw.inheritsFrom, instancePath);
            }
        } catch (Exception e) {
            CoreLogger.get().warn(LOG, "No se pudo leer " + versionFile + ": " + e.getMessage());
        }
        return versionId;
    }

    private VersionInfo loadLocalVersionInfo(InstallRequest request) {
        String versionId = request.version;
        Path[] candidates = request.hasSharedPath()
                ? new Path[]{
                        Path.of(request.resolvedInstancePath())
                                .resolve("versions").resolve(versionId).resolve(versionId + ".json"),
                        Path.of(request.sharedPath).toAbsolutePath()
                                .resolve("versions").resolve(versionId).resolve(versionId + ".json")
                  }
                : new Path[]{
                        Path.of(request.resolvedInstancePath())
                                .resolve("versions").resolve(versionId).resolve(versionId + ".json")
                  };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                try {
                    String      json = Files.readString(candidate);
                    VersionInfo info = GSON.fromJson(json, VersionInfo.class);
                    CoreLogger.get().info(LOG, "Using local cache: " + candidate);
                    return info;
                } catch (Exception e) {
                    CoreLogger.get().warn(LOG, "Cannot parse local cache " + candidate + ": " + e.getMessage());
                }
            }
        }
        return null;
    }

    private static InstallRequest withNoDownloads(InstallRequest original) {
        InstallRequest r   = new InstallRequest();
        r.version          = original.version;
        r.instancePath     = original.instancePath;
        r.sharedPath       = original.sharedPath;
        r.verifySHA1       = original.verifySHA1;
        r.maxThreads       = original.maxThreads;
        r.debug            = original.debug;
        r.modloader        = original.modloader;
        r.modloaderVersion = original.modloaderVersion;

        InstallRequest.DownloadOptions dl = new InstallRequest.DownloadOptions();
        dl.client = dl.libraries = dl.assets = dl.natives = dl.jvm = false;
        r.download = dl;
        return r;
    }

    private static Map<String, Long> countByCategory(List<DownloadTask> tasks) {
        return Map.of(
                "client",      tasks.stream().filter(t -> "client".equals(t.category)).count(),
                "libraries",   tasks.stream().filter(t -> "library".equals(t.category)).count(),
                "assets",      tasks.stream().filter(t -> "asset".equals(t.category)).count(),
                "natives",     tasks.stream().filter(t -> "native".equals(t.category)).count(),
                "asset_index", tasks.stream().filter(t -> "asset_index".equals(t.category)).count());
    }
}
