package dev.novastep.core.minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.novastep.core.log.CoreLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class InstanceMetadata {

    private static final String LOG = "InstanceMetadata";
    private static final String LEGACY_FILENAME = "novacore_engine.bin";
    private static final String DEFAULT_BRAND = "Third_Party_Launcher";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERIFY_DAYS = 7;

    public static class Metadata {
        public String instanceId;
        public String mcVersion;
        public String modLoader;
        public String modLoaderVersion;
        public String instancePath;
        public String brandName;
        public String createdAt;
        public String lastVerifiedAt;
        public String nextVerifyAt;
        public List<InstalledEntry> installedVersions = new ArrayList<>();
        public Map<String, Object> extra = new LinkedHashMap<>();

        public static class InstalledEntry {
            public String installedAt;
            public String mcVersion;
            public String modLoader;
            public String modLoaderVersion;
        }
    }

    private InstanceMetadata() {
    }

    public static String getFileName(String brandName) {
        String name = (brandName == null || brandName.isBlank()) ? DEFAULT_BRAND : brandName;
        return name.toLowerCase().replace(" ", "_").replace("-", "_") + ".json";
    }

    private static void migrateIfNecessary(Path instancePath, Path brandedFile) {
        Path legacyFile = instancePath.resolve(LEGACY_FILENAME);
        if (!Files.exists(brandedFile) && Files.exists(legacyFile)) {
            try {
                Files.move(legacyFile, brandedFile, StandardCopyOption.REPLACE_EXISTING);
                CoreLogger.get().info(LOG,
                        "Migrated legacy metadata (" + LEGACY_FILENAME + ") to " + brandedFile.getFileName());
            } catch (IOException ex) {
                CoreLogger.get().warn(LOG, "Failed to migrate legacy metadata: " + ex.getMessage());
            }
        }
    }

    public static Metadata readOrCreate(Path instancePath, String brandName, String mcVersion,
            String modLoader, String modLoaderVersion) {
        Path file = instancePath.resolve(getFileName(brandName));
        migrateIfNecessary(instancePath, file);

        if (Files.exists(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Metadata m = GSON.fromJson(json, Metadata.class);
                if (m != null)
                    return m;
            } catch (Exception ex) {
                CoreLogger.get().warn(LOG, "Corrupted metadata at " + file + " — recreating: " + ex.getMessage());
            }
        }
        return create(instancePath, brandName, mcVersion, modLoader, modLoaderVersion);
    }

    public static Metadata create(Path instancePath, String brandName, String mcVersion,
            String modLoader, String modLoaderVersion) {
        Metadata m = new Metadata();
        m.instanceId = UUID.randomUUID().toString();
        m.brandName = brandName != null ? brandName : DEFAULT_BRAND;
        m.mcVersion = mcVersion;
        m.modLoader = modLoader;
        m.modLoaderVersion = modLoaderVersion;
        m.instancePath = instancePath.toAbsolutePath().toString();
        m.createdAt = Instant.now().toString();
        m.lastVerifiedAt = m.createdAt;
        m.nextVerifyAt = Instant.now().plus(VERIFY_DAYS, ChronoUnit.DAYS).toString();

        Metadata.InstalledEntry entry = new Metadata.InstalledEntry();
        entry.installedAt = m.createdAt;
        entry.mcVersion = mcVersion;
        entry.modLoader = modLoader;
        entry.modLoaderVersion = modLoaderVersion;
        m.installedVersions.add(entry);

        save(instancePath, m);
        return m;
    }

    public static void recordVerification(Path instancePath, Metadata m) {
        m.lastVerifiedAt = Instant.now().toString();
        m.nextVerifyAt = Instant.now().plus(VERIFY_DAYS, ChronoUnit.DAYS).toString();
        save(instancePath, m);
    }

    public static boolean isVerificationDue(Metadata m) {
        if (m.nextVerifyAt == null)
            return true;
        try {
            return Instant.now().isAfter(Instant.parse(m.nextVerifyAt));
        } catch (Exception ignored) {
            return true;
        }
    }

    public static void save(Path instancePath, Metadata m) {
        Path file = instancePath.resolve(getFileName(m.brandName));
        try {
            Files.createDirectories(instancePath);
            Files.writeString(file, GSON.toJson(m), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            CoreLogger.get().warn(LOG, "Failed to save metadata to " + file + ": " + ex.getMessage());
        }
    }

    public static Optional<Metadata> read(Path instancePath, String brandName) {
        Path file = instancePath.resolve(getFileName(brandName));
        migrateIfNecessary(instancePath, file);

        if (!Files.exists(file))
            return Optional.empty();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Metadata m = GSON.fromJson(json, Metadata.class);
            return Optional.ofNullable(m);
        } catch (Exception ex) {
            CoreLogger.get().warn(LOG, "Failed to read metadata at " + file + ": " + ex.getMessage());
            return Optional.empty();
        }
    }
}
