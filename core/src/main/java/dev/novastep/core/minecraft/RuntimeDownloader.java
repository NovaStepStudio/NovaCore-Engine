package dev.novastep.core.minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.downloader.DownloadTask;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class RuntimeDownloader {

    private static final String JAVA_ALL_URL = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final int TIMEOUT_SEC = 30;
    private static final String LOG = "RuntimeDownloader";

    private final HttpClient      http;
    private final DownloadManager downloadManager;
    private final EventBroadcaster broadcaster;

    public RuntimeDownloader(DownloadManager downloadManager, EventBroadcaster broadcaster) {
        this.downloadManager = downloadManager;
        this.broadcaster     = broadcaster;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public String downloadRuntime(String sessionId, String component,
            Path instancePath, Path sharedPath)
            throws IOException, InterruptedException, ExecutionException {

        CoreLogger.get().info(LOG, "[" + sessionId + "] Fetching Java runtime: " + component);
        broadcaster.emitDebug(sessionId, "Buscando Java runtime: " + component);

        String platform = detectPlatform();
        CoreLogger.get().info(LOG, "[" + sessionId + "] Platform: " + platform);
        broadcaster.emitDebug(sessionId, "Plataforma: " + platform);

        JsonObject allJson = fetchJson(JAVA_ALL_URL).getAsJsonObject();

        if (!allJson.has(platform))
            throw new IOException("No Java runtime available for platform: " + platform);

        JsonObject platformObj = allJson.getAsJsonObject(platform);
        if (!platformObj.has(component))
            throw new IOException("Component '" + component + "' not available for " + platform);

        var runtimeArray = platformObj.getAsJsonArray(component);
        if (runtimeArray.isEmpty())
            throw new IOException("Empty runtime list for " + component + " on " + platform);

        JsonObject runtimeMeta    = runtimeArray.get(runtimeArray.size() - 1).getAsJsonObject();
        String javaVersionName    = runtimeMeta.getAsJsonObject("version").get("name").getAsString();
        String manifestUrl        = runtimeMeta.getAsJsonObject("manifest").get("url").getAsString();

        Path javaRoot = resolveRuntimeRoot(javaVersionName, instancePath, sharedPath);
        boolean isShared = sharedPath != null && javaRoot.startsWith(sharedPath);

        CoreLogger.get().info(LOG, "[" + sessionId + "] Java version: " + javaVersionName + " → " + javaRoot + (isShared ? " [SHARED]" : " [INSTANCE]"));
        broadcaster.emitDebug(sessionId, "Java version: " + javaVersionName + " → " + javaRoot + (isShared ? " (compartido)" : " (instancia)"));

        String execPath = getJavaExecutable(javaRoot);
        if (Files.exists(Path.of(execPath))) {
            CoreLogger.get().info(LOG, "[" + sessionId + "] Java runtime already installed: " + execPath);
            broadcaster.emitDebug(sessionId, "Java runtime ya instalado: " + execPath);
            return execPath;
        }

        JsonObject manifestJson = fetchJson(manifestUrl).getAsJsonObject();
        JsonObject files        = manifestJson.getAsJsonObject("files");

        List<DownloadTask> tasks = new ArrayList<>();
        for (var entry : files.entrySet()) {
            String     relativePath = entry.getKey();
            JsonObject fileObj      = entry.getValue().getAsJsonObject();
            String     type         = fileObj.get("type").getAsString();

            if ("directory".equals(type)) {
                Files.createDirectories(javaRoot.resolve(relativePath));
                continue;
            }
            if (!"file".equals(type)) continue;

            JsonObject downloads = fileObj.getAsJsonObject("downloads");
            JsonObject raw       = downloads.getAsJsonObject("raw");

            tasks.add(new DownloadTask(
                    sessionId, "runtime", relativePath,
                    raw.get("url").getAsString(),
                    javaRoot.resolve(relativePath),
                    raw.get("size").getAsLong(),
                    raw.get("sha1").getAsString()));
        }

        CoreLogger.get().info(LOG, "[" + sessionId + "] Runtime download tasks: " + tasks.size());
        broadcaster.emit("runtime_download_start", Map.of(
                "session",      sessionId,
                "component",    component,
                "javaVersion",  javaVersionName,
                "totalFiles",   tasks.size(),
                "shared",       isShared));

        String runtimeSessionId = downloadManager.createSession();
        downloadManager.submitAll(runtimeSessionId, tasks).get();

        markExecutables(javaRoot);

        CoreLogger.get().info(LOG, "[" + sessionId + "] Java runtime ready: " + execPath);
        broadcaster.emit("runtime_download_complete", Map.of(
                "session",     sessionId,
                "javaVersion", javaVersionName,
                "javaPath",    execPath,
                "shared",      isShared));
        broadcaster.emitDebug(sessionId, "Java runtime listo: " + execPath);
        return execPath;
    }

    public String downloadRuntime(String sessionId, String component, Path instancePath)
            throws IOException, InterruptedException, ExecutionException {
        return downloadRuntime(sessionId, component, instancePath, null);
    }

    public static String getJavaExecutable(Path javaRoot) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return javaRoot.toAbsolutePath()
                .resolve("bin")
                .resolve(isWindows ? "java.exe" : "java")
                .toString();
    }

    public static String findExistingRuntime(Path instancePath, Path sharedPath) {
        Path[] roots = sharedPath != null
                ? new Path[]{ sharedPath.resolve("runtime"), instancePath.resolve("runtime") }
                : new Path[]{ instancePath.resolve("runtime") };

        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.list(root)) {
                var found = stream
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("java-"))
                        .map(p -> getJavaExecutable(p))
                        .filter(exec -> Files.exists(Path.of(exec)))
                        .findFirst();
                if (found.isPresent()) return found.get();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Path resolveRuntimeRoot(String javaVersionName,
            Path instancePath, Path sharedPath) {
        if (sharedPath != null) {
            return sharedPath.toAbsolutePath()
                    .resolve("runtime")
                    .resolve("java-" + javaVersionName);
        }
        return instancePath.toAbsolutePath()
                .resolve("runtime")
                .resolve("java-" + javaVersionName);
    }

    private static void markExecutables(Path javaRoot) {
        try {
            Path bin = javaRoot.resolve("bin");
            if (Files.isDirectory(bin)) {
                try (var stream = Files.list(bin)) {
                    stream.forEach(f -> f.toFile().setExecutable(true));
                }
            }
        } catch (Exception ignored) {}
    }

    private static String detectPlatform() {
        String os   = System.getProperty("os.name",  "").toLowerCase();
        String arch = System.getProperty("os.arch",  "").toLowerCase();
        if (os.contains("win"))
            return arch.equals("amd64") || arch.equals("x86_64") ? "windows-x64" : "windows-x86";
        if (os.contains("mac"))
            return arch.equals("aarch64") || arch.equals("arm64") ? "mac-os-arm64" : "mac-os";
        if (arch.equals("aarch64") || arch.equals("arm64")) return "linux-arm64";
        if (arch.equals("arm")    || arch.equals("armv7l")) return "linux-arm32";
        if (arch.equals("i386")   || arch.equals("i686"))   return "linux-i386";
        return "linux";
    }

    private JsonElement fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .header("User-Agent", "novacore-engine/" + dev.novastep.core.CoreVersion.get() + " (NovaStepStudios)")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200)
            throw new IOException("HTTP " + res.statusCode() + " fetching: " + url);
        return com.google.gson.JsonParser.parseString(res.body());
    }
}
