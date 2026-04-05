package dev.novastep.core.minecraft;

import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.server.LaunchRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MinecraftVerifier {

    private static final String LOG = "MinecraftVerifier";

    private MinecraftVerifier() {}

    public static final class VerificationResult {
        public final boolean ok;
        public final List<MissingComponent> missing;

        private VerificationResult(List<MissingComponent> missing) {
            this.missing = List.copyOf(missing);
            this.ok = missing.isEmpty();
        }
    }

    public record MissingComponent(String category, String description, Path path) {
        @Override public String toString() {
            return "[" + category + "] " + description + " → " + path;
        }
    }

    public static VerificationResult verify(LaunchRequest req, VersionInfo versionInfo, String vanillaVersionId) {
        List<MissingComponent> missing = new ArrayList<>();

        Path instancePath  = Path.of(req.resolvedInstancePath()).toAbsolutePath();
        Path librariesPath = req.resolvedLibrariesPath().toAbsolutePath();
        Path assetsPath    = req.resolvedAssetsPath().toAbsolutePath();

        Path clientJar = instancePath.resolve("versions")
                .resolve(vanillaVersionId).resolve(vanillaVersionId + ".jar");
        if (!Files.isRegularFile(clientJar)) {
            missing.add(new MissingComponent("client", vanillaVersionId + ".jar", clientJar));
        }

        Path nativesDir = instancePath.resolve("versions")
                .resolve(vanillaVersionId).resolve("natives");
        if (!Files.isDirectory(nativesDir) || isDirEmpty(nativesDir)) {
            missing.add(new MissingComponent("natives", "natives/", nativesDir));
        }

        if (versionInfo != null && versionInfo.libraries != null) {
            int downloadableMissing = 0;
            int processorMissing    = 0;

            for (VersionInfo.Library lib : versionInfo.libraries) {
                if (!lib.isAllowed()) continue;
                if (lib.downloads == null || lib.downloads.artifact == null) continue;
                if (TaskBuilder.isNativeLib(lib)) continue;
                VersionInfo.Artifact art = lib.downloads.artifact;
                if (art.path == null) continue;

                boolean exists = Files.isRegularFile(librariesPath.resolve(art.path));
                if (!exists) {
                    boolean hasUrl = art.url != null && !art.url.isBlank();
                    if (hasUrl) downloadableMissing++;
                    else        processorMissing++;
                }
            }

            if (downloadableMissing > 0) {
                missing.add(new MissingComponent("libraries",
                        downloadableMissing + " library jar(s) missing", librariesPath));
            }
            if (processorMissing > 0) {
                missing.add(new MissingComponent("installer-outputs",
                        processorMissing + " installer-generated file(s) missing — re-run modloader install",
                        librariesPath));
            }
        }

        if (versionInfo != null && versionInfo.assetIndex != null) {
            Path indexFile = assetsPath.resolve("indexes")
                    .resolve(versionInfo.assetIndex.id + ".json");
            if (!Files.isRegularFile(indexFile)) {
                missing.add(new MissingComponent("assets",
                        "asset index " + versionInfo.assetIndex.id + ".json", indexFile));
            }
        }

        boolean javaFound = false;
        if (req.javaPath != null && !req.javaPath.isBlank() && !req.javaPath.equals("java")) {
            javaFound = Files.isRegularFile(Path.of(req.javaPath));
        }
        if (!javaFound) {
            Path sharedBase = (req.sharedPath != null && !req.sharedPath.isBlank())
                    ? Path.of(req.sharedPath).toAbsolutePath() : null;
            String found = RuntimeDownloader.findExistingRuntime(instancePath, sharedBase);
            javaFound = (found != null && Files.isRegularFile(Path.of(found)));
        }
        if (!javaFound) {
            Path searchedDir = (req.sharedPath != null && !req.sharedPath.isBlank())
                    ? Path.of(req.sharedPath).toAbsolutePath().resolve("runtime")
                    : instancePath.resolve("runtime");
            missing.add(new MissingComponent("runtime", "java executable", searchedDir));
        }

        if (!missing.isEmpty()) {
            CoreLogger.get().warn(LOG, "Verification FAILED for version " + req.version
                    + " — " + missing.size() + " component(s) missing:");
            missing.forEach(c -> CoreLogger.get().warn(LOG, "  " + c));
        } else {
            CoreLogger.get().info(LOG, "Verification OK for version " + req.version
                    + " (vanilla base: " + vanillaVersionId + ")");
        }

        return new VerificationResult(missing);
    }

    public static VerificationResult verify(LaunchRequest req, VersionInfo versionInfo) {
        return verify(req, versionInfo, req.version);
    }

    private static boolean isDirEmpty(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }
}
