package dev.novastep.core.minecraft.manifest;

import com.google.gson.Gson;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.models.AssetIndexManifest;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.minecraft.models.VersionManifest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ManifestClient {

    private static final String ROOT_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_SEC = 30;
    private static final String LOG = "ManifestClient";

    private final HttpClient http;

    public ManifestClient() {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }

    public VersionManifest fetchRootManifest() throws IOException, InterruptedException {
        CoreLogger.get().info(LOG, "Fetching root manifest...");
        return fetchJson(ROOT_MANIFEST_URL, VersionManifest.class);
    }

    public VersionInfo fetchVersionInfo(String versionUrl) throws IOException, InterruptedException {
        CoreLogger.get().debug(LOG, "Fetching version info: " + versionUrl);
        return fetchJson(versionUrl, VersionInfo.class);
    }

    public VersionInfo fetchVersionById(String versionId) throws IOException, InterruptedException {
        VersionManifest root = fetchRootManifest();
        VersionManifest.VersionEntry entry = root.findById(versionId);
        if (entry == null) {
            throw new IllegalArgumentException("Version '" + versionId + "' not found. Latest: " + root.latest.release);
        }
        CoreLogger.get().info(LOG, "Resolved '" + versionId + "' → " + entry.url);
        return fetchVersionInfo(entry.url);
    }

    public AssetIndexManifest fetchAssetIndex(VersionInfo.AssetIndex assetIndexInfo)
            throws IOException, InterruptedException {
        CoreLogger.get().info(LOG, "Fetching asset index '" + assetIndexInfo.id +
            "' (" + assetIndexInfo.size / 1024 + " KB)...");
        return fetchJson(assetIndexInfo.url, AssetIndexManifest.class);
    }

    private <T> T fetchJson(String url, Class<T> clazz) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(TIMEOUT_SEC))
            .header("User-Agent", "novacore-engine/1.0 (NovaStepStudios)")
            .header("Accept", "application/json")
            .GET().build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Network error fetching: " + url + " → " + e.getMessage(), e);
        }

        if (response.statusCode() != 200)
            throw new IOException("HTTP " + response.statusCode() + " fetching: " + url);

        return GSON.fromJson(response.body(), clazz);
    }
}
