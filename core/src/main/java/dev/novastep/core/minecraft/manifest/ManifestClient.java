package dev.novastep.core.minecraft.manifest;

import com.google.gson.Gson;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.version.AssetIndexManifest;
import dev.novastep.core.minecraft.version.VersionInfo;
import dev.novastep.core.minecraft.version.VersionManifest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class ManifestClient {

    private static final String LOG          = "ManifestClient";
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private static final Gson       GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public VersionManifest fetchManifest() throws IOException, InterruptedException {
        String json = get(MANIFEST_URL);
        return GSON.fromJson(json, VersionManifest.class);
    }

    public VersionInfo fetchVersionById(String versionId) throws IOException, InterruptedException {
        VersionManifest manifest = fetchManifest();
        String url = manifest.versions.stream()
                .filter(v -> v.id.equals(versionId))
                .findFirst()
                .map(v -> v.url)
                .orElseThrow(() -> new IOException("Versión no encontrada en manifest Mojang: " + versionId));
        return fetchVersionFromUrl(url);
    }

    public VersionInfo fetchVersionWithInheritance(String versionId)
            throws IOException, InterruptedException {
        return fetchVersionWithInheritance(versionId, null);
    }

    public VersionInfo fetchVersionWithInheritance(String versionId, Path localBasePath)
            throws IOException, InterruptedException {

        VersionInfo version = fetchVersionByIdFlexible(versionId, localBasePath);
        return resolveInheritance(version, localBasePath);
    }

    public VersionInfo resolveInheritance(VersionInfo version)
            throws IOException, InterruptedException {
        return resolveInheritance(version, null);
    }

    public VersionInfo resolveInheritance(VersionInfo version, Path localBasePath)
            throws IOException, InterruptedException {
        if (version.inheritsFrom == null || version.inheritsFrom.isBlank()) {
            return version;
        }

        CoreLogger.get().info(LOG, "Resolviendo herencia: " + version.id + " → " + version.inheritsFrom);

        VersionInfo parent = fetchVersionByIdFlexible(version.inheritsFrom, localBasePath);
        parent = resolveInheritance(parent, localBasePath);
        return VersionMerger.merge(parent, version);
    }

    public AssetIndexManifest fetchAssetIndex(VersionInfo.AssetIndex assetIndex)
            throws IOException, InterruptedException {
        String json = get(assetIndex.url);
        return GSON.fromJson(json, AssetIndexManifest.class);
    }

    private VersionInfo fetchVersionByIdFlexible(String versionId, Path localBasePath)
            throws IOException, InterruptedException {
        try {
            VersionManifest manifest = fetchManifest();
            String url = manifest.versions.stream()
                    .filter(v -> v.id.equals(versionId))
                    .findFirst()
                    .map(v -> v.url)
                    .orElse(null);

            if (url != null) {
                VersionInfo info = fetchVersionFromUrl(url);
                CoreLogger.get().debug(LOG, "Versión cargada desde Mojang: " + versionId);
                return info;
            }
        } catch (IOException networkEx) {
            CoreLogger.get().error(LOG, "Network error while fetching version " + versionId + " from Mojang remote manifest. Falling back to local search if possible.", networkEx);
        }

        if (localBasePath != null) {
            Path localJson = localBasePath
                    .resolve("versions")
                    .resolve(versionId)
                    .resolve(versionId + ".json");

            if (Files.exists(localJson)) {
                try {
                    String json = Files.readString(localJson, StandardCharsets.UTF_8);
                    VersionInfo info = GSON.fromJson(json, VersionInfo.class);
                    CoreLogger.get().info(LOG, "Versión cargada desde JSON local: " + localJson);
                    return info;
                } catch (Exception ex) {
                    CoreLogger.get().error(LOG, "Failed to parse local version JSON for id=" + versionId + " at " + localJson, ex);
                    throw new IOException("JSON local corrupto para '" + versionId
                            + "': " + localJson + " — " + ex.getMessage(), ex);
                }
            }
        }

        throw new IOException(
                "Versión '" + versionId + "' no encontrada en Mojang ni en disco local. "
                + (localBasePath != null
                        ? "Buscado en: " + localBasePath.resolve("versions").resolve(versionId)
                        : "localBasePath no configurado."));
    }

    private VersionInfo fetchVersionFromUrl(String url) throws IOException, InterruptedException {
        String json = get(url);
        return GSON.fromJson(json, VersionInfo.class);
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " fetching: " + url);
        }
        return res.body();
    }
}
