package dev.novastep.core.modloader.provider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.modloader.ModLoaderProvider;
import dev.novastep.core.modloader.installer.InstallerExecutor;
import dev.novastep.core.modloader.installer.MavenCoordinate;
import dev.novastep.core.modloader.model.DownloadPlan;
import dev.novastep.core.modloader.model.ExecutionPlan;
import dev.novastep.core.modloader.model.InstalledLoader;
import dev.novastep.core.util.JavaResolver;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

abstract class AbstractForgeProvider implements ModLoaderProvider {

    private static final Gson       GSON = new Gson();
    protected static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    protected abstract String installerUrl(String loaderVersionId);
    protected abstract String mavenRepoBase();
    protected abstract List<String> listAllVersions() throws IOException, InterruptedException;
    protected abstract List<String> filterForMinecraft(List<String> all, String mcVersion);
    protected abstract String versionIdForInstaller(String mcVersion, String loaderVersion);

    @Override
    public DownloadPlan resolveDownload(
            String mcVersion, String loaderVersion,
            Path instancePath, Path librariesPath) throws IOException, InterruptedException {

        String versionId     = versionIdForInstaller(mcVersion, loaderVersion);
        String installerUrl  = installerUrl(versionId);
        Path   installerDest = instancePath.resolve("installers").resolve(versionId + "-installer.jar");

        Files.createDirectories(installerDest.getParent());

        List<DownloadPlan.Entry> entries = new ArrayList<>();
        entries.add(DownloadPlan.Entry.installer(versionId + "-installer.jar", installerUrl, installerDest));

        return DownloadPlan.withInstaller(entries, installerDest);
    }

    @Override
    public boolean requiresInstallerRun() {
        return true;
    }

    @Override
    public void runInstaller(
            String sessionId,
            InstalledLoader loader,
            Path instancePath,
            Path librariesPath,
            Path minecraftJar,
            EventBroadcaster broadcaster) throws Exception {

        Path   installerJar = Path.of(loader.installerJarPath);
        String javaExec     = JavaResolver.resolve(instancePath);

        String canonicalId = readCanonicalVersionId(installerJar);
        if (canonicalId != null && !canonicalId.equals(loader.versionJsonId)) {
            CoreLogger.get().info(name(), "[" + sessionId + "] Canonical version ID: "
                    + canonicalId + " (was: " + loader.versionJsonId + ")");
            loader.versionJsonId = canonicalId;
        }

        CoreLogger.get().info(name(), "[" + sessionId + "] Running installer: " + installerJar.getFileName());
        broadcaster.emit("modloader_install_start", Map.of(
                "sessionId", sessionId,
                "loader",    name(),
                "version",   loader.loaderVersion));

        InstallerExecutor.execute(
                sessionId, installerJar, librariesPath,
                minecraftJar, instancePath, javaExec, mavenRepoBase(), broadcaster);

        extractVersionJson(sessionId, loader, installerJar, instancePath);

        broadcaster.emit("modloader_install_done", Map.of(
                "sessionId", sessionId,
                "loader",    name(),
                "versionId", loader.versionJsonId));
    }

    private String readCanonicalVersionId(Path installerJar) {
        try (JarFile jar = new JarFile(installerJar.toFile())) {
            java.util.jar.JarEntry entry = jar.getJarEntry("version.json");
            if (entry == null) return null;
            String json = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            return obj.has("id") ? obj.get("id").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void extractVersionJson(
            String sessionId,
            InstalledLoader loader,
            Path installerJar,
            Path instancePath) throws IOException {

        String versionId  = loader.versionJsonId;
        Path   versionDir = instancePath.resolve("versions").resolve(versionId);
        Path   versionFile = versionDir.resolve(versionId + ".json");

        if (Files.exists(versionFile)) {
            CoreLogger.get().info(name(), "[" + sessionId + "] version.json already exists: " + versionId);
            return;
        }

        try (JarFile jar = new JarFile(installerJar.toFile())) {
            java.util.jar.JarEntry entry = jar.getJarEntry("version.json");
            if (entry != null) {
                Files.createDirectories(versionDir);
                String rawJson = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                Files.writeString(versionFile, rawJson, StandardCharsets.UTF_8);
                CoreLogger.get().info(name(), "[" + sessionId + "] Saved version.json: " + versionId);
            } else {
                CoreLogger.get().warn(name(), "[" + sessionId + "] version.json not found in installer JAR");
            }
        }
    }

    @Override
    public ExecutionPlan buildExecution(
            InstalledLoader loader,
            VersionInfo vanillaInfo,
            Path instancePath,
            Path librariesPath) {

        String versionId   = loader.versionJsonId;
        Path   versionFile = instancePath.resolve("versions").resolve(versionId).resolve(versionId + ".json");

        if (!Files.exists(versionFile)) {
            CoreLogger.get().warn(name(), "Version JSON not found: " + versionFile);
            return null;
        }

        try {
            String     raw     = Files.readString(versionFile, StandardCharsets.UTF_8);
            JsonObject profile = GSON.fromJson(raw, JsonObject.class);

            String mainClass = profile.has("mainClass")
                    ? profile.get("mainClass").getAsString()
                    : vanillaInfo.mainClass;

            List<Path>   classpath     = resolveClasspath(profile, librariesPath);
            boolean      useModulePath = "cpw.mods.bootstraplauncher.BootstrapLauncher".equals(mainClass);

            return useModulePath
                    ? ExecutionPlan.forBootstrapLauncher(mainClass, classpath, List.of(), List.of())
                    : ExecutionPlan.fromVersionJson(mainClass, classpath, List.of(), List.of());

        } catch (IOException ex) {
            CoreLogger.get().error(name(), "Failed to read version JSON: " + versionFile, ex);
            return null;
        }
    }

    private List<Path> resolveClasspath(JsonObject profile, Path librariesPath) {
        List<Path> entries = new ArrayList<>();
        if (!profile.has("libraries")) return entries;
        for (JsonElement el : profile.getAsJsonArray("libraries")) {
            JsonObject lib  = el.getAsJsonObject();
            String     name = lib.has("name") ? lib.get("name").getAsString() : null;
            if (name == null) continue;
            try {
                Path jar = MavenCoordinate.parse(name).toLocalPath(librariesPath);
                if (Files.exists(jar)) entries.add(jar);
            } catch (IllegalArgumentException ignored) {}
        }
        return entries;
    }

    protected String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " from: " + url);
        }
        return res.body();
    }

    protected List<String> parseMavenMetadataVersions(String xml) {
        List<String> versions = new ArrayList<>();
        int start = 0;
        while (true) {
            int open  = xml.indexOf("<version>", start);
            if (open == -1) break;
            int close = xml.indexOf("</version>", open);
            if (close == -1) break;
            versions.add(xml.substring(open + 9, close).trim());
            start = close + 10;
        }
        return versions;
    }
}
