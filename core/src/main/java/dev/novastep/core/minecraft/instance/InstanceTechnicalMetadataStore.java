package dev.novastep.core.minecraft.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.novastep.core.log.CoreLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class InstanceTechnicalMetadataStore {

    private static final String LOG = "InstanceTechnicalMetadataStore";
    public static final String FILENAME = "instance.metadata.json";
    private static final int VERIFY_DAYS = 7;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class TechnicalMetadata {
        public String instanceId;
        public String instancePath;
        public String createdAt;
        public String lastVerifiedAt;
        public String nextVerifyAt;
        public List<InstalledVersion> installedVersions = new ArrayList<>();

        public static final class InstalledVersion {
            public String installedAt;
            public String mcVersion;
            public Boolean lastPlayed;
            public String lastPlayedAt;
        }
    }

    private InstanceTechnicalMetadataStore() {}

    public static Path file(Path instancePath) {
        return instancePath.toAbsolutePath().resolve(FILENAME);
    }

    public static Optional<TechnicalMetadata> read(Path instancePath) {
        Path f = file(instancePath);
        if (!Files.exists(f))
            return Optional.empty();
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            return Optional.ofNullable(GSON.fromJson(json, TechnicalMetadata.class));
        } catch (Exception ex) {
            CoreLogger.get().warn(LOG, "Failed to read " + f + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    public static TechnicalMetadata readOrCreate(Path instancePath, String mcVersion) {
        return read(instancePath).orElseGet(() -> create(instancePath, mcVersion));
    }

    public static TechnicalMetadata create(Path instancePath, String mcVersion) {
        TechnicalMetadata m = new TechnicalMetadata();
        m.instanceId = UUID.randomUUID().toString();
        m.instancePath = instancePath.toAbsolutePath().toString();
        m.createdAt = Instant.now().toString();
        m.lastVerifiedAt = m.createdAt;
        m.nextVerifyAt = Instant.now().plus(VERIFY_DAYS, ChronoUnit.DAYS).toString();

        save(instancePath, m);
        return m;
    }

    public static void recordInstall(Path instancePath, TechnicalMetadata m, String mcVersion) {
        if (m.installedVersions == null)
            m.installedVersions = new ArrayList<>();

        if (!m.installedVersions.isEmpty()) {
            TechnicalMetadata.InstalledVersion last = m.installedVersions.get(m.installedVersions.size() - 1);
            if (last != null && mcVersion != null && mcVersion.equals(last.mcVersion)) {
                save(instancePath, m);
                return;
            }
        }

        TechnicalMetadata.InstalledVersion iv = new TechnicalMetadata.InstalledVersion();
        iv.installedAt = Instant.now().toString();
        iv.mcVersion = mcVersion;
        iv.lastPlayed = false;
        iv.lastPlayedAt = null;
        m.installedVersions.add(iv);
        save(instancePath, m);
    }

    public static void recordVerification(Path instancePath, TechnicalMetadata m) {
        m.lastVerifiedAt = Instant.now().toString();
        m.nextVerifyAt = Instant.now().plus(VERIFY_DAYS, ChronoUnit.DAYS).toString();
        save(instancePath, m);
    }

    public static void markLastPlayed(Path instancePath, String mcVersion) {
        Optional<TechnicalMetadata> opt = read(instancePath);
        if (opt.isEmpty())
            return;
        TechnicalMetadata m = opt.get();
        if (m.installedVersions == null || m.installedVersions.isEmpty())
            return;

        String now = Instant.now().toString();
        for (var e : m.installedVersions) {
            if (e != null) e.lastPlayed = false;
        }

        for (int i = m.installedVersions.size() - 1; i >= 0; i--) {
            var e = m.installedVersions.get(i);
            if (e != null && mcVersion != null && mcVersion.equals(e.mcVersion)) {
                e.lastPlayed = true;
                e.lastPlayedAt = now;
                break;
            }
        }
        save(instancePath, m);
    }

    public static void save(Path instancePath, TechnicalMetadata m) {
        Path f = file(instancePath);
        try {
            Files.createDirectories(instancePath);
            Files.writeString(f, GSON.toJson(m), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ex) {
            CoreLogger.get().warn(LOG, "Failed to save " + f + ": " + ex.getMessage());
        }
    }
}

