package dev.novastep.core.minecraft;

import com.google.gson.Gson;
import dev.novastep.core.downloader.DownloadTask;
import dev.novastep.core.minecraft.models.AssetIndexManifest;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.server.InstallRequest;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TaskBuilder {

    private static final Gson GSON = new Gson();

    private final boolean includeClient, includeLibraries, includeAssets, includeNatives;
    private final EventBroadcaster broadcaster;

    private final Path librariesBase;
    
    private final Path assetsBase;
    
    private final Path instanceBase;

    public TaskBuilder(boolean includeClient, boolean includeLibraries,
                       boolean includeAssets,  boolean includeNatives) {
        this(includeClient, includeLibraries, includeAssets, includeNatives, null, null, null, null);
    }

    public TaskBuilder(boolean includeClient, boolean includeLibraries,
                       boolean includeAssets,  boolean includeNatives,
                       EventBroadcaster broadcaster) {
        this(includeClient, includeLibraries, includeAssets, includeNatives, broadcaster, null, null, null);
    }

    public TaskBuilder(boolean includeClient, boolean includeLibraries,
                       boolean includeAssets,  boolean includeNatives,
                       EventBroadcaster broadcaster,
                       Path instanceBase, Path librariesBase, Path assetsBase) {
        this.includeClient = includeClient;
        this.includeLibraries = includeLibraries;
        this.includeAssets = includeAssets;
        this.includeNatives = includeNatives;
        this.broadcaster = broadcaster;
        this.instanceBase = instanceBase;
        this.librariesBase = librariesBase;
        this.assetsBase = assetsBase;
    }

    public static TaskBuilder fromRequest(InstallRequest req, EventBroadcaster broadcaster) {
        Path instance = Path.of(req.resolvedInstancePath()).toAbsolutePath();
        Path libraries = req.resolvedLibrariesPath().toAbsolutePath();
        Path assets = req.resolvedAssetsPath().toAbsolutePath();
        return new TaskBuilder(
            req.shouldDownloadClient(),
            req.shouldDownloadLibraries(),
            req.shouldDownloadAssets(),
            req.shouldDownloadNatives(),
            broadcaster, instance, libraries, assets
        );
    }

    public List<DownloadTask> build(String sessionId, VersionInfo versionInfo, AssetIndexManifest assetIndex) {
        Path instPath = instanceBase;
        Path libPath = librariesBase != null ? librariesBase : instPath.resolve("libraries");
        Path astPath = assetsBase != null ? assetsBase : instPath.resolve("assets");

        List<DownloadTask> tasks = new ArrayList<>();
        if (includeClient) addClientTask(tasks, sessionId, versionInfo, instPath);
        if (includeLibraries) addLibraryTasks(tasks,sessionId, versionInfo, libPath);
        if (includeNatives) addNativeTasks(tasks, sessionId, versionInfo, instPath, libPath);
        if (includeAssets && versionInfo.assetIndex != null) {
            addAssetIndexTask(tasks, sessionId, versionInfo, astPath);
            if (assetIndex != null) addAssetTasks(tasks, sessionId, assetIndex, astPath);
        }
        return tasks;
    }

    public List<DownloadTask> build(String sessionId, VersionInfo versionInfo, AssetIndexManifest assetIndex, Path instancePath) {
        Path libPath = librariesBase != null ? librariesBase : instancePath.resolve("libraries");
        Path astPath = assetsBase != null ? assetsBase : instancePath.resolve("assets");

        List<DownloadTask> tasks = new ArrayList<>();
        if (includeClient) addClientTask(tasks,   sessionId, versionInfo, instancePath);
        if (includeLibraries) addLibraryTasks(tasks, sessionId, versionInfo, libPath);
        if (includeNatives) addNativeTasks(tasks,  sessionId, versionInfo, instancePath, libPath);
        if (includeAssets && versionInfo.assetIndex != null) {
            addAssetIndexTask(tasks, sessionId, versionInfo, astPath);
            if (assetIndex != null) addAssetTasks(tasks, sessionId, assetIndex, astPath);
        }
        return tasks;
    }

    public static void saveVersionJson(VersionInfo info, Path instancePath) throws IOException {
        Path jsonPath = instancePath.resolve("versions").resolve(info.id).resolve(info.id + ".json");
        Files.createDirectories(jsonPath.getParent());
        Files.writeString(jsonPath, GSON.toJson(info));
    }

    private void addClientTask(List<DownloadTask> tasks, String sessionId, VersionInfo info, Path instPath) {
        if (info.downloads == null || info.downloads.client == null) return;
        VersionInfo.Artifact c = info.downloads.client;
        if (!isValidArtifact(c)) return;
        Path dest = instPath.resolve("versions").resolve(info.id).resolve(info.id + ".jar");
        tasks.add(DownloadTask.client(sessionId, info.id, c.url, dest, c.size, c.sha1));
    }

    private void addLibraryTasks(List<DownloadTask> tasks, String sessionId, VersionInfo info, Path libPath) {
        if (info.libraries == null) return;
        for (VersionInfo.Library lib : info.libraries) {
            if (!lib.isAllowed()) continue;
            if (isNativeLib(lib)) continue;

            if (lib.downloads != null && lib.downloads.artifact != null
                    && lib.downloads.artifact.url != null
                    && !lib.downloads.artifact.url.isBlank()) {
                VersionInfo.Artifact artifact = lib.downloads.artifact;
                if (!isValidArtifact(artifact)) continue;
                String relativePath = artifact.path != null ? artifact.path : mavenToPath(lib.name);
                tasks.add(DownloadTask.library(
                        sessionId, shortName(lib.name), artifact.url,
                        libPath.resolve(relativePath), artifact.size, artifact.sha1));
                continue;
            }

            if (lib.name == null || lib.name.isBlank()) continue;

            LibraryResolver.Resolution resolution = LibraryResolver.resolve(lib);
            if (!resolution.found()) {
                if (broadcaster != null) {
                    broadcaster.emitDebug(sessionId,
                            "Library sin URL y sin resolver en repos: " + lib.name);
                }
                continue;
            }

            LibraryResolver.Resolution.Found found = (LibraryResolver.Resolution.Found) resolution;
            Path dest = libPath.resolve(found.path());
            tasks.add(DownloadTask.library(
                    sessionId, shortName(lib.name), found.url(),
                    dest, -1L, null));
        }
    }

    private void addNativeTasks(List<DownloadTask> tasks, String sessionId, VersionInfo info, Path instPath, Path libPath) {
        if (info.libraries == null) return;
        String os = currentOs(), arch = currentArch();
        int found = 0, skipped = 0;

        for (VersionInfo.Library lib : info.libraries) {
            if (!lib.isAllowed()) continue;
            if (lib.downloads == null) continue;
            if (!isNativeLib(lib)) continue;

            NativeArtifactResult result = findNativeArtifact(lib, os, arch);
            if (result == null) continue;
            if (!isValidArtifact(result.artifact)) { skipped++; continue; }

            String relativePath = result.artifact.path != null
                ? result.artifact.path
                : mavenToPath(lib.name, result.classifierKey);

            Path dest = libPath.resolve(relativePath);
            tasks.add(DownloadTask.nativeLib(
                sessionId, shortName(lib.name) + "[" + os + "]",
                result.artifact.url, dest, result.artifact.size, result.artifact.sha1
            ));
            found++;
        }

        if (broadcaster != null) {
            broadcaster.emitDebug(sessionId,
                "Natives: " + found + " tasks, " + skipped + " placeholders skipped " +
                "(os=" + os + ", arch=" + arch + ", libPath=" +
                (librariesBase != null ? "shared" : "instance") + ")");
        }
    }

    public static boolean isNativeLib(VersionInfo.Library lib) {
        if (lib.downloads == null) return false;
        String os = currentOs();
        if (lib.natives != null && lib.natives.containsKey(os)) return true;
        if (lib.downloads.classifiers != null) {
            for (String k : lib.downloads.classifiers.keySet())
                if (k.startsWith("natives-" + os)) return true;
        }
        if (lib.downloads.artifact != null && lib.downloads.artifact.path != null)
            if (lib.downloads.artifact.path.toLowerCase().contains("natives-" + os)) return true;
        if (lib.name != null) {
            String n = lib.name.toLowerCase();
            if (n.contains(":natives-" + os)) return true;
            if (n.contains(":natives") &&
                !n.contains(":natives-linux") && !n.contains(":natives-osx") &&
                !n.contains(":natives-windows") && !n.contains(":natives-macos")) return true;
        }
        return false;
    }

    private static NativeArtifactResult findNativeArtifact(VersionInfo.Library lib, String os, String arch) {
        if (lib.downloads == null) return null;

        if (lib.natives != null && lib.natives.containsKey(os)) {
            String template = lib.natives.get(os);
            String archNum  = arch.equals("x86") ? "32" : "64";
            String classWithArch = template.replace("${arch}", archNum);
            String classNoArch   = "natives-" + os;

            if (lib.downloads.classifiers != null) {
                VersionInfo.Artifact a = lib.downloads.classifiers.get(classWithArch);
                if (a != null) return new NativeArtifactResult(a, classWithArch);
                a = lib.downloads.classifiers.get(classNoArch);
                if (a != null) return new NativeArtifactResult(a, classNoArch);
                for (var e : lib.downloads.classifiers.entrySet())
                    if (e.getKey().startsWith("natives-" + os))
                        return new NativeArtifactResult(e.getValue(), e.getKey());
            }
        }

        if (lib.downloads.classifiers != null) {
            for (var e : lib.downloads.classifiers.entrySet()) {
                if (e.getKey().startsWith("natives-" + os) && archMatches(e.getKey(), arch))
                    return new NativeArtifactResult(e.getValue(), e.getKey());
            }
            for (var e : lib.downloads.classifiers.entrySet()) {
                if (e.getKey().startsWith("natives-" + os))
                    return new NativeArtifactResult(e.getValue(), e.getKey());
            }
        }

        if (lib.downloads.artifact != null && lib.downloads.artifact.url != null) {
            String p = lib.downloads.artifact.path;
            String n = lib.name;
            boolean pathOk = p != null && p.toLowerCase().contains("natives-" + os);
            boolean nameOk = n != null && n.toLowerCase().contains(":natives-" + os);
            boolean generic= n != null && n.toLowerCase().contains(":natives")
                && !n.toLowerCase().contains(":natives-linux")
                && !n.toLowerCase().contains(":natives-osx")
                && !n.toLowerCase().contains(":natives-windows")
                && !n.toLowerCase().contains(":natives-macos");

            if ((pathOk || nameOk) && archMatches(p != null ? p : n, arch))
                return new NativeArtifactResult(lib.downloads.artifact, null);
            if (pathOk || nameOk || generic)
                return new NativeArtifactResult(lib.downloads.artifact, null);
        }
        return null;
    }

    private record NativeArtifactResult(VersionInfo.Artifact artifact, String classifierKey) {}

    public static boolean isValidArtifact(VersionInfo.Artifact a) {
        if (a == null) return false;
        if (a.url == null || a.url.isBlank()) return false;
        return a.size >= 100;  
    }

    private static boolean archMatches(String name, String arch) {
        if (name == null) return true;
        String l = name.toLowerCase();
        if (arch.equals("x86_64")) {
            if (l.contains("arm64") || l.contains("aarch64")) return false;
            if (l.contains("-x86-") || l.endsWith("-x86.jar")) return false;
            return true;
        }
        if (arch.equals("arm64")) return l.contains("arm64") || l.contains("aarch64");
        if (arch.equals("x86"))   return !l.contains("arm64") && !l.contains("x86_64");
        return true;
    }

    private void addAssetIndexTask(List<DownloadTask> tasks, String sessionId,
                                   VersionInfo info, Path astPath) {
        VersionInfo.AssetIndex idx = info.assetIndex;
        tasks.add(DownloadTask.assetIndex(sessionId, idx.id, idx.url,
            astPath.resolve("indexes").resolve(idx.id + ".json"), idx.size, idx.sha1));
    }

    private void addAssetTasks(List<DownloadTask> tasks, String sessionId,
                               AssetIndexManifest assetIndex, Path astPath) {
        if (assetIndex.objects == null) return;
        for (var entry : assetIndex.objects.entrySet()) {
            AssetIndexManifest.Asset asset = entry.getValue();
            tasks.add(DownloadTask.asset(sessionId, entry.getKey(), asset.downloadUrl(),
                astPath.resolve("objects").resolve(asset.objectPath()), asset.size, asset.hash));
        }
    }

    public static String currentOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    public static String currentArch() {
        String a = System.getProperty("os.arch", "").toLowerCase();
        if (a.equals("amd64") || a.equals("x86_64")) return "x86_64";
        if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
        if (a.equals("x86") || a.equals("i386") || a.equals("i686")) return "x86";
        return a;
    }

    public static String mavenToPath(String coord) { return mavenToPath(coord, null); }
    public static String mavenToPath(String coord, String classifier) {
        String[] p = coord.split(":");
        if (p.length < 3) return coord.replace(":", "/") + ".jar";
        String group = p[0].replace('.', '/'), artifact = p[1], version = p[2];
        String classif = (classifier != null && !classifier.isBlank()) ? "-" + classifier : (p.length > 3 ? "-" + p[3] : "");
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classif + ".jar";
    }
    private static String shortName(String c) { String[] p = c.split(":"); return p.length >= 2 ? p[1] : c; }
}