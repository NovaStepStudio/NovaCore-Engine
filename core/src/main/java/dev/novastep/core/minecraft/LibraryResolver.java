package dev.novastep.core.minecraft;

import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.version.VersionInfo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class LibraryResolver {

    private static final String LOG = "LibraryResolver";

    private static final String[] MAVEN_REPOS = {
        "https://libraries.minecraft.net/",
        "https://repo1.maven.org/maven2/",
        "https://maven.minecraftforge.net/",
        "https://maven.fabricmc.net/",
        "https://maven.neoforged.net/releases/",
        "https://maven.quiltmc.org/repository/release/",
        "https://jitpack.io/",
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private LibraryResolver() {}

    public sealed interface Resolution permits Resolution.Found, Resolution.NotFound {

        record Found(String url, String path, String repo) implements Resolution {
            @Override public boolean found() { return true; }
        }

        record NotFound(String name) implements Resolution {
            @Override public boolean found() { return false; }
        }

        boolean found();
    }

    public static Resolution resolve(VersionInfo.Library lib) {
        if (lib.downloads != null
                && lib.downloads.artifact != null
                && lib.downloads.artifact.url != null
                && !lib.downloads.artifact.url.isBlank()) {

            String mavenPath = lib.downloads.artifact.path != null
                    ? lib.downloads.artifact.path
                    : mavenCoordinateToPath(lib.name);

            return new Resolution.Found(lib.downloads.artifact.url, mavenPath, "explicit");
        }

        if (lib.name == null || lib.name.isBlank()) {
            return new Resolution.NotFound("<unnamed>");
        }

        String mavenPath = mavenCoordinateToPath(lib.name);
        if (mavenPath == null) {
            CoreLogger.get().warn(LOG, "Coordenada Maven inválida: '" + lib.name + "'");
            return new Resolution.NotFound(lib.name);
        }

        return probeRepositories(lib.name, mavenPath);
    }

    public static List<ResolvedLibrary> resolveAll(VersionInfo info, java.nio.file.Path librariesPath) {
        List<ResolvedLibrary> result = new ArrayList<>();
        if (info.libraries == null) return result;

        for (VersionInfo.Library lib : info.libraries) {
            if (!lib.isAllowed()) continue;
            if (TaskBuilder.isNativeLib(lib)) continue;

            Resolution resolution = resolve(lib);
            if (!resolution.found()) {
                CoreLogger.get().warn(LOG, "No se pudo resolver: " + lib.name
                        + " — omitida. ¿Dependencia privada/local?");
                continue;
            }

            Resolution.Found found = (Resolution.Found) resolution;
            java.nio.file.Path localPath = librariesPath.resolve(found.path());

            String sha1 = (lib.downloads != null && lib.downloads.artifact != null)
                    ? lib.downloads.artifact.sha1 : null;
            long size = (lib.downloads != null && lib.downloads.artifact != null)
                    ? lib.downloads.artifact.size : -1;

            result.add(new ResolvedLibrary(lib.name, found.url(), found.path(), localPath, sha1, size));
        }

        return result;
    }

    public static String mavenCoordinateToPath(String coord) {
        if (coord == null) return null;
        String[] parts = coord.split(":");
        if (parts.length < 3) return null;

        String groupPath   = parts[0].replace('.', '/');
        String artifactId  = parts[1];
        String version     = parts[2];
        String classifier  = parts.length > 3 ? parts[3] : null;

        String jarName = artifactId + "-" + version
                + (classifier != null ? "-" + classifier : "")
                + ".jar";

        return groupPath + "/" + artifactId + "/" + version + "/" + jarName;
    }

    private static Resolution probeRepositories(String name, String mavenPath) {
        for (String repoBase : MAVEN_REPOS) {
            String url = repoBase + mavenPath;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(3))
                        .build();

                HttpResponse<Void> res = HTTP.send(req, HttpResponse.BodyHandlers.discarding());

                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    CoreLogger.get().info(LOG, "Resuelto: " + name + " → " + repoBase);
                    return new Resolution.Found(url, mavenPath, repoBase);
                }
            } catch (Exception ex) {
                CoreLogger.get().debug(LOG, "HEAD fallido para " + url + ": " + ex.getMessage());
            }
        }

        CoreLogger.get().warn(LOG, "Sin resolver en ningún repo: " + name + " (" + mavenPath + ")");
        return new Resolution.NotFound(name);
    }

    public record ResolvedLibrary(
            String name,
            String url,
            String mavenPath,
            java.nio.file.Path localPath,
            String sha1,
            long   size
    ) {}
}
