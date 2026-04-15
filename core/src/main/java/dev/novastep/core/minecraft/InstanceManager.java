package dev.novastep.core.minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.novastep.core.log.CoreLogger;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class InstanceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String INSTANCE_METADATA_FILE = "instance.json";
    private static final String LOG = "InstanceManager";

    private final Path instancesRoot;

    public InstanceManager(Path instancesRoot) {
        this.instancesRoot = instancesRoot.toAbsolutePath();
    }

    public static class InstanceMeta {
        public String id;
        public String name;
        public String mcVersion;
        public String modLoader;
        public String modLoaderVersion;
        public String javaPath;
        public int minMemoryMb;
        public int maxMemoryMb;
        public boolean hardwareAccel;
        public String gcPreset;
        public List<String> jvmArgs;
        public List<String> extraGameArgs;
        public Map<String, String> jvmProperties;
        public String launcherName;
        public String launcherVersion;
        public String serverHost;
        public Integer serverPort;
        public Boolean disableMultiplayer;
        public Boolean disableChat;
        public String customGameDir;
        public Map<String, Object> customFields;
        public String createdAt;
        public String updatedAt;
        public String lastPlayedAt;
        public long totalPlayTimeMs;
        public String iconPath;

        public InstanceMeta() {
            this.id = UUID.randomUUID().toString();
            this.modLoader = "vanilla";
            this.minMemoryMb = 512;
            this.maxMemoryMb = 2048;
            this.hardwareAccel = false;
            this.jvmArgs = new ArrayList<>();
            this.extraGameArgs = new ArrayList<>();
            this.jvmProperties = new HashMap<>();
            this.customFields = new HashMap<>();
            this.createdAt = Instant.now().toString();
            this.updatedAt = Instant.now().toString();
        }
    }

    public InstanceMeta create(String name, String mcVersion) throws IOException {
        return create(name, mcVersion, null);
    }

    public InstanceMeta create(String name, String mcVersion, InstanceMeta overrides) throws IOException {
        InstanceMeta meta = overrides != null ? overrides : new InstanceMeta();
        meta.id = UUID.randomUUID().toString();
        meta.name = name;
        meta.mcVersion = mcVersion;
        meta.createdAt = Instant.now().toString();
        meta.updatedAt = meta.createdAt;

        Path instanceDir = instancesRoot.resolve(sanitizeName(name) + "-" + meta.id.substring(0, 8));
        Files.createDirectories(instanceDir);

        for (String subdir : List.of(
                "versions",
                "libraries",
                "assets/indexes",
                "assets/objects",
                "game",
                "game/mods",
                "game/config",
                "game/saves",
                "game/resourcepacks",
                "game/shaderpacks",
                "runtime",
                "logs")) {
            Files.createDirectories(instanceDir.resolve(subdir));
        }

        writeMetadata(instanceDir, meta);
        CoreLogger.get().info(LOG, "Created instance: " + name + " [" + meta.id + "] at " + instanceDir);
        return meta;
    }

    public List<Map<String, Object>> listAll() throws IOException {
        if (!Files.isDirectory(instancesRoot))
            return List.of();
        List<Map<String, Object>> instances = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesRoot)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir))
                    continue;
                Path metaFile = dir.resolve(INSTANCE_METADATA_FILE);
                if (!Files.exists(metaFile))
                    continue;
                try {
                    InstanceMeta meta = readMetadata(dir);
                    instances.add(metaToMap(meta, dir));
                } catch (Exception e) {
                    CoreLogger.get().warn(LOG, "Failed to read instance metadata at: " + dir);
                }
            }
        }
        instances.sort(Comparator.comparing(m -> (String) m.getOrDefault("name", "")));
        return instances;
    }

    public Map<String, Object> get(String idOrName) throws IOException {
        Path dir = findInstanceDir(idOrName);
        if (dir == null)
            return null;
        return metaToMap(readMetadata(dir), dir);
    }

    public InstanceMeta update(String idOrName, InstanceMeta updates) throws IOException {
        Path dir = findInstanceDir(idOrName);
        if (dir == null)
            throw new IllegalArgumentException("Instance not found: " + idOrName);
        InstanceMeta existing = readMetadata(dir);

        if (updates.name != null)
            existing.name = updates.name;
        if (updates.mcVersion != null)
            existing.mcVersion = updates.mcVersion;
        if (updates.modLoader != null)
            existing.modLoader = updates.modLoader;
        if (updates.modLoaderVersion != null)
            existing.modLoaderVersion = updates.modLoaderVersion;
        if (updates.javaPath != null)
            existing.javaPath = updates.javaPath;
        if (updates.gcPreset != null)
            existing.gcPreset = updates.gcPreset;
        if (updates.jvmArgs != null)
            existing.jvmArgs = updates.jvmArgs;
        if (updates.extraGameArgs != null)
            existing.extraGameArgs = updates.extraGameArgs;
        if (updates.jvmProperties != null)
            existing.jvmProperties = updates.jvmProperties;
        if (updates.launcherName != null)
            existing.launcherName = updates.launcherName;
        if (updates.launcherVersion != null)
            existing.launcherVersion = updates.launcherVersion;
        if (updates.serverHost != null)
            existing.serverHost = updates.serverHost;
        if (updates.serverPort != null)
            existing.serverPort = updates.serverPort;
        if (updates.customGameDir != null)
            existing.customGameDir = updates.customGameDir;
        if (updates.disableMultiplayer != null)
            existing.disableMultiplayer = updates.disableMultiplayer;
        if (updates.disableChat != null)
            existing.disableChat = updates.disableChat;
        if (updates.iconPath != null)
            existing.iconPath = updates.iconPath;
        if (updates.minMemoryMb > 0)
            existing.minMemoryMb = updates.minMemoryMb;
        if (updates.maxMemoryMb > 0)
            existing.maxMemoryMb = updates.maxMemoryMb;
        existing.hardwareAccel = updates.hardwareAccel;
        existing.updatedAt = Instant.now().toString();

        writeMetadata(dir, existing);
        CoreLogger.get().info(LOG, "Updated instance: " + idOrName);
        return existing;
    }

    public boolean delete(String idOrName) throws IOException {
        Path dir = findInstanceDir(idOrName);
        if (dir == null)
            return false;
        deleteRecursive(dir);
        CoreLogger.get().info(LOG, "Deleted instance: " + idOrName);
        return true;
    }

    public void recordPlaySession(String idOrName, long durationMs) throws IOException {
        Path dir = findInstanceDir(idOrName);
        if (dir == null)
            return;
        InstanceMeta meta = readMetadata(dir);
        meta.lastPlayedAt = Instant.now().toString();
        meta.totalPlayTimeMs += durationMs;
        meta.updatedAt = meta.lastPlayedAt;
        writeMetadata(dir, meta);
    }

    public String getInstancePath(String idOrName) throws IOException {
        Path dir = findInstanceDir(idOrName);
        if (dir == null)
            throw new IllegalArgumentException("Instance not found: " + idOrName);
        return dir.toAbsolutePath().toString();
    }

    public InstanceMeta getMetadata(String idOrName) throws IOException {
        Path dir = findInstanceDir(idOrName);
        if (dir == null)
            return null;
        return readMetadata(dir);
    }

    private Path findInstanceDir(String idOrName) throws IOException {
        if (!Files.isDirectory(instancesRoot))
            return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesRoot)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir))
                    continue;
                Path metaFile = dir.resolve(INSTANCE_METADATA_FILE);
                if (!Files.exists(metaFile))
                    continue;
                try {
                    InstanceMeta meta = readMetadata(dir);
                    if (idOrName.equals(meta.id) || idOrName.equals(meta.name))
                        return dir;
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private void writeMetadata(Path dir, InstanceMeta meta) throws IOException {
        Files.writeString(dir.resolve(INSTANCE_METADATA_FILE), GSON.toJson(meta));
    }

    private InstanceMeta readMetadata(Path dir) throws IOException {
        String json = Files.readString(dir.resolve(INSTANCE_METADATA_FILE));
        return GSON.fromJson(json, InstanceMeta.class);
    }

    private Map<String, Object> metaToMap(InstanceMeta meta, Path dir) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", meta.id);
        m.put("name", meta.name);
        m.put("mcVersion", meta.mcVersion);
        m.put("modLoader", meta.modLoader);
        m.put("modLoaderVersion", meta.modLoaderVersion);
        m.put("minMemoryMb", meta.minMemoryMb);
        m.put("maxMemoryMb", meta.maxMemoryMb);
        m.put("hardwareAccel", meta.hardwareAccel);
        m.put("gcPreset", meta.gcPreset);
        m.put("launcherName", meta.launcherName);
        m.put("launcherVersion", meta.launcherVersion);
        m.put("serverHost", meta.serverHost);
        m.put("serverPort", meta.serverPort);
        m.put("jvmArgs", meta.jvmArgs);
        m.put("extraGameArgs", meta.extraGameArgs);
        m.put("createdAt", meta.createdAt);
        m.put("lastPlayedAt", meta.lastPlayedAt);
        m.put("totalPlayHours", String.format("%.1f", meta.totalPlayTimeMs / 3600000.0));
        m.put("path", dir.toAbsolutePath().toString());
        Path clientJar = dir.resolve("versions").resolve(meta.mcVersion).resolve(meta.mcVersion + ".jar");
        m.put("installed", Files.exists(clientJar));
        return m;
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream)
                    deleteRecursive(entry);
            }
        }
        Files.delete(path);
    }
}
