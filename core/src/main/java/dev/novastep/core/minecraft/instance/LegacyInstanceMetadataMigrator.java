package dev.novastep.core.minecraft.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.novastep.core.log.CoreLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LegacyInstanceMetadataMigrator {

    private static final String LOG = "LegacyInstanceMetadataMigrator";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String LEGACY_BIN = "novacore_engine.bin";
    private static final String DEFAULT_BRAND_FILE = "third_party_launcher.json";

    private static final class LegacyMetadata {
        public String instanceId;
        public String instancePath;
        public String createdAt;
        public String lastVerifiedAt;
        public String nextVerifyAt;
        public List<LegacyInstalledEntry> installedVersions;

        private static final class LegacyInstalledEntry {
            public String installedAt;
            public String mcVersion;
        }
    }

    private LegacyInstanceMetadataMigrator() {
    }

    public static Optional<InstanceTechnicalMetadataStore.TechnicalMetadata> migrateIfPresent(
            Path instancePath,
            String launcherBrandName) {

        Path target = InstanceTechnicalMetadataStore.file(instancePath);
        if (Files.exists(target))
            return InstanceTechnicalMetadataStore.read(instancePath);

        List<Path> candidates = new ArrayList<>();
        candidates.add(instancePath.resolve(LEGACY_BIN));
        if (launcherBrandName != null && !launcherBrandName.isBlank()) {
            String branded = launcherBrandName.toLowerCase()
                    .replace(" ", "_")
                    .replace("-", "_") + ".json";
            candidates.add(instancePath.resolve(branded));
        }
        candidates.add(instancePath.resolve(DEFAULT_BRAND_FILE));

        for (Path c : candidates) {
            if (!Files.exists(c))
                continue;
            Optional<InstanceTechnicalMetadataStore.TechnicalMetadata> migrated = tryMigrateFrom(instancePath, c);
            if (migrated.isPresent())
                return migrated;
        }
        return Optional.empty();
    }

    private static Optional<InstanceTechnicalMetadataStore.TechnicalMetadata> tryMigrateFrom(
            Path instancePath,
            Path legacyFile) {
        try {
            String raw = Files.readString(legacyFile, StandardCharsets.UTF_8);
            LegacyMetadata lm = GSON.fromJson(raw, LegacyMetadata.class);
            if (lm == null || lm.instanceId == null || lm.instanceId.isBlank())
                return Optional.empty();

            InstanceTechnicalMetadataStore.TechnicalMetadata tm = new InstanceTechnicalMetadataStore.TechnicalMetadata();
            tm.instanceId = lm.instanceId;
            tm.instancePath = lm.instancePath != null ? lm.instancePath : instancePath.toAbsolutePath().toString();
            tm.createdAt = lm.createdAt;
            tm.lastVerifiedAt = lm.lastVerifiedAt;
            tm.nextVerifyAt = lm.nextVerifyAt;
            tm.installedVersions = new ArrayList<>();

            if (lm.installedVersions != null) {
                for (var e : lm.installedVersions) {
                    InstanceTechnicalMetadataStore.TechnicalMetadata.InstalledVersion iv = new InstanceTechnicalMetadataStore.TechnicalMetadata.InstalledVersion();
                    iv.installedAt = e.installedAt;
                    iv.mcVersion = e.mcVersion;
                    tm.installedVersions.add(iv);
                }
            }

            InstanceTechnicalMetadataStore.save(instancePath, tm);
            CoreLogger.get().info(LOG, "Migrated legacy metadata " + legacyFile.getFileName()
                    + " → " + InstanceTechnicalMetadataStore.FILENAME);
            return Optional.of(tm);
        } catch (Exception ex) {
            CoreLogger.get().warn(LOG, "Legacy metadata migration failed at " + legacyFile + ": " + ex.getMessage());
            return Optional.empty();
        }
    }
}
