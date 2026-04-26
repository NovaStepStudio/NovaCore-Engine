package dev.novastep.core.modloader.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.version.VersionInfo;
import dev.novastep.core.modloader.ModLoaderProvider;
import dev.novastep.core.modloader.installer.MavenCoordinate;
import dev.novastep.core.modloader.model.ModLoaderModels.DownloadPlan;
import dev.novastep.core.modloader.model.ModLoaderModels.ExecutionPlan;
import dev.novastep.core.modloader.model.ModLoaderModels.InstalledLoader;
import dev.novastep.core.modloader.model.ModLoaderModels.LoaderVersion;
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

abstract class AbstractFabricProvider implements ModLoaderProvider {

    private static final Gson       GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final String FABRIC_MAVEN_BASE = "https://maven.fabricmc.net/";

    protected abstract String versionsEndpoint(String mcVersion);
    protected abstract String profileEndpoint(String mcVersion, String loaderVersion);

    @Override
    public List<LoaderVersion> getVersions(String mcVersion) throws IOException, InterruptedException {
        String    json = get(versionsEndpoint(mcVersion));
        JsonArray arr  = GSON.fromJson(json, JsonArray.class);
        List<LoaderVersion> result = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject obj    = el.getAsJsonObject();
            JsonObject loader = obj.has("loader") ? obj.getAsJsonObject("loader") : obj;
            String  version   = loader.get("version").getAsString();
            boolean stable    = loader.has("stable") ? loader.get("stable").getAsBoolean() : true;
            result.add(new LoaderVersion(version, mcVersion, stable));
        }
        return result;
    }

    @Override
    public DownloadPlan resolveDownload(
            String mcVersion, String loaderVersion,
            Path instancePath, Path librariesPath) throws IOException, InterruptedException {

        String profileJson = get(profileEndpoint(mcVersion, loaderVersion));
        String versionId   = name() + "-" + mcVersion + "-" + loaderVersion;
        Path   versionDir  = instancePath.resolve("versions").resolve(versionId);
        Path   versionFile = versionDir.resolve(versionId + ".json");

        Files.createDirectories(versionDir);
        Files.writeString(versionFile, profileJson, StandardCharsets.UTF_8);

        JsonObject profile  = GSON.fromJson(profileJson, JsonObject.class);
        JsonArray  libraries = profile.has("libraries")
                ? profile.getAsJsonArray("libraries") : new JsonArray();

        List<DownloadPlan.Entry> entries = new ArrayList<>();
        for (JsonElement el : libraries) {
            JsonObject lib = el.getAsJsonObject();
            resolveLibraryEntry(lib, librariesPath, entries);
        }

        return DownloadPlan.profileOnly(entries);
    }

    private void resolveLibraryEntry(
            JsonObject lib, Path librariesPath, List<DownloadPlan.Entry> entries) {

        if (lib.has("downloads")) {
            JsonObject downloads = lib.getAsJsonObject("downloads");
            if (!downloads.has("artifact")) return;
            JsonObject artifact = downloads.getAsJsonObject("artifact");
            String url  = artifact.has("url")  ? artifact.get("url").getAsString()  : null;
            String path = artifact.has("path") ? artifact.get("path").getAsString() : null;
            if (url == null || url.isBlank() || path == null) return;
            long   size = artifact.has("size") ? artifact.get("size").getAsLong() : -1;
            String sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
            String name = lib.has("name") ? lib.get("name").getAsString() : path;
            entries.add(DownloadPlan.Entry.library(name, url, librariesPath.resolve(path), size, sha1));
            return;
        }

        if (lib.has("name") && lib.has("url")) {
            String coord = lib.get("name").getAsString();
            String base  = lib.get("url").getAsString();
            if (base.isBlank()) base = FABRIC_MAVEN_BASE;
            try {
                MavenCoordinate mc = MavenCoordinate.parse(coord);
                String url  = mc.toRemoteUrl(base);
                Path   dest = mc.toLocalPath(librariesPath);
                entries.add(DownloadPlan.Entry.library(coord, url, dest, -1, null));
            } catch (IllegalArgumentException ex) {
                CoreLogger.get().warn(name(), "Failed to parse Maven coordinate: " + coord + " — " + ex.getMessage());
            }
            return;
        }

        if (lib.has("name")) {
            String coord = lib.get("name").getAsString();
            try {
                MavenCoordinate mc = MavenCoordinate.parse(coord);
                String url  = mc.toRemoteUrl(FABRIC_MAVEN_BASE);
                Path   dest = mc.toLocalPath(librariesPath);
                entries.add(DownloadPlan.Entry.library(coord, url, dest, -1, null));
            } catch (IllegalArgumentException ex) {
                CoreLogger.get().warn(name(), "Failed to parse Maven coordinate: " + coord + " — " + ex.getMessage());
            }
        }
    }

    @Override
    public boolean requiresInstallerRun() {
        return false;
    }

    @Override
    public void runInstaller(String sessionId, InstalledLoader loader, Path instancePath,
                             Path librariesPath, Path minecraftJar, EventBroadcaster broadcaster) {
    }

    @Override
    public ExecutionPlan buildExecution(
            InstalledLoader loader, VersionInfo vanillaInfo,
            Path instancePath, Path librariesPath) {

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

            List<Path>   classpath = buildClasspath(profile, librariesPath, vanillaInfo, instancePath);
            List<String> jvmArgs   = new ArrayList<>();
            List<String> gameArgs  = new ArrayList<>();

            if (profile.has("arguments")) {
                JsonObject args = profile.getAsJsonObject("arguments");
                collectStringArgs(args.has("jvm")  ? args.getAsJsonArray("jvm")  : new JsonArray(), jvmArgs);
                collectStringArgs(args.has("game") ? args.getAsJsonArray("game") : new JsonArray(), gameArgs);
            }

            return ExecutionPlan.fromVersionJson(mainClass, classpath, jvmArgs, gameArgs);

        } catch (IOException ex) {
            CoreLogger.get().error(name(), "Failed to read version JSON: " + versionFile, ex);
            return null;
        }
    }

    private List<Path> buildClasspath(
            JsonObject profile, Path librariesPath, VersionInfo vanillaInfo, Path instancePath) {

        List<Path> entries = new ArrayList<>();

        if (profile.has("libraries")) {
            for (JsonElement el : profile.getAsJsonArray("libraries")) {
                JsonObject lib = el.getAsJsonObject();

                if (lib.has("downloads")) {
                    JsonObject downloads = lib.getAsJsonObject("downloads");
                    if (!downloads.has("artifact")) continue;
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    if (!artifact.has("path")) continue;
                    Path jar = librariesPath.resolve(artifact.get("path").getAsString());
                    if (Files.exists(jar)) entries.add(jar);

                } else if (lib.has("name")) {
                    try {
                        Path jar = MavenCoordinate.parse(lib.get("name").getAsString())
                                .toLocalPath(librariesPath);
                        if (Files.exists(jar)) entries.add(jar);
                    } catch (IllegalArgumentException ex) {
                        CoreLogger.get().warn(name(), "Failed to parse library Maven coordinate: " + lib.get("name").getAsString() + " — " + ex.getMessage());
                    }
                }
            }
        }

        if (vanillaInfo != null && vanillaInfo.id != null) {
            Path vanillaJar = instancePath.resolve("versions")
                    .resolve(vanillaInfo.id).resolve(vanillaInfo.id + ".jar");
            if (Files.exists(vanillaJar)) entries.add(vanillaJar);
        }

        return entries;
    }

    private void collectStringArgs(JsonArray arr, List<String> target) {
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive()) target.add(el.getAsString());
        }
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
}
