package dev.novastep.core.minecraft.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.novastep.core.log.CoreLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InstanceConfigStore {

    private static final String LOG = "InstanceConfigStore";
    public static final String FILENAME = "instance.config.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class InstanceConfig {
        public String id;
        public InstanceMetadata instanceMetadata = new InstanceMetadata();
        public ConfigInstance configInstance = new ConfigInstance();
    }

    public static final class InstanceMetadata {
        public String createdAt;
        public String updatedAt;
        public Long totalPlayTimeMs = 0L;
        public FrontendMetadata frontend = new FrontendMetadata();
    }

    public static final class FrontendMetadata {
        public String name = "";
        public String description = "";
        public String icon = "";
        public String hero = "";
    }

    public static final class ConfigInstance {
        public Integer minMemoryMb;
        public Integer maxMemoryMb;
        public Boolean hardwareAccel;
        public String gcPreset;
        public String gpuPreference;
        public String javaPath;
        public WindowConfig window = new WindowConfig();
        public JvmConfig jvm = new JvmConfig();
        public List<String> extraGameArgs = new ArrayList<>();
        public Map<String, Object> customFields = new LinkedHashMap<>();
    }

    public static final class WindowConfig {
        public Boolean fullscreen = false;
        public Integer width = 854;
        public Integer height = 480;
    }

    public static final class JvmConfig {
        public Integer minMemoryMb;
        public Integer maxMemoryMb;
        public List<String> extraArgs = new ArrayList<>();
        public List<String> prependArgs = new ArrayList<>();
        public Map<String, String> jvmProperties = new LinkedHashMap<>();
    }

    private InstanceConfigStore() {
    }

    public static Path file(Path instancePath) {
        return instancePath.toAbsolutePath().resolve(FILENAME);
    }

    public static Optional<InstanceConfig> read(Path instancePath) {
        return readFromFile(file(instancePath));
    }

    public static Optional<InstanceConfig> readFromFile(Path f) {
        if (!Files.exists(f))
            return Optional.empty();
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            InstanceConfig cfg = GSON.fromJson(json, InstanceConfig.class);

            // Basic self-repair for null sub-objects if somehow they are missing
            if (cfg != null) {
                if (cfg.instanceMetadata == null)
                    cfg.instanceMetadata = new InstanceMetadata();
                if (cfg.instanceMetadata.frontend == null)
                    cfg.instanceMetadata.frontend = new FrontendMetadata();
                if (cfg.configInstance == null)
                    cfg.configInstance = new ConfigInstance();
            }
            return Optional.ofNullable(cfg);
        } catch (Exception ex) {
            CoreLogger.get().error(LOG, "Failed to read or parse instance configuration file at " + f, ex);
            return Optional.empty();
        }
    }

    public static InstanceConfig readOrCreate(Path instancePath,
            InstanceTechnicalMetadataStore.TechnicalMetadata tech,
            String mcVersion) {
        Optional<InstanceConfig> existing = read(instancePath);
        if (existing.isPresent())
            return existing.get();

        InstanceConfig c = new InstanceConfig();
        c.id = tech != null ? tech.instanceId : null;

        c.instanceMetadata.createdAt = Instant.now().toString();
        c.instanceMetadata.updatedAt = c.instanceMetadata.createdAt;
        c.instanceMetadata.totalPlayTimeMs = 0L;
        c.instanceMetadata.frontend.name = "";
        c.instanceMetadata.frontend.description = "";
        c.instanceMetadata.frontend.icon = "";
        c.instanceMetadata.frontend.hero = "";

        c.configInstance.minMemoryMb = DefaultInstanceConfig.MIN_MEMORY_MB;
        c.configInstance.maxMemoryMb = DefaultInstanceConfig.MAX_MEMORY_MB;
        c.configInstance.hardwareAccel = DefaultInstanceConfig.HARDWARE_ACCEL;
        c.configInstance.gcPreset = DefaultInstanceConfig.GC_PRESET;
        c.configInstance.gpuPreference = DefaultInstanceConfig.GPU_PREFERENCE;
        c.configInstance.javaPath = DefaultInstanceConfig.JAVA_PATH;

        c.configInstance.jvm.minMemoryMb = DefaultInstanceConfig.MIN_MEMORY_MB;
        c.configInstance.jvm.maxMemoryMb = DefaultInstanceConfig.MAX_MEMORY_MB;

        save(instancePath, c);
        return c;
    }

    public static void save(Path instancePath, InstanceConfig c) {
        Path f = file(instancePath);
        try {
            Files.createDirectories(instancePath);
            Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ex) {
            CoreLogger.get().error(LOG,
                    "Failed to save instance configuration to " + f + " for instance at " + instancePath, ex);
        }
    }

}
