package dev.novastep.core.server.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.world.WorldModels.WorldMetadata;
import dev.novastep.core.minecraft.world.WorldModels.WorldFlags;
import dev.novastep.core.server.HttpUtils;
import dev.novastep.core.util.NbtReader;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.novastep.core.util.NbtReader.*;

public class QuickPlayHandler implements HttpHandler {

    private static final String LOG = "QuickPlayHandler";
    private final Path instancesDir;
    private final Gson gson = new Gson();

    private static final Map<String, CachedWorld> CACHE = new ConcurrentHashMap<>();

    private record CachedWorld(WorldMetadata metadata, long lastModified) {
    }

    public QuickPlayHandler(Path instancesDir) {
        this.instancesDir = instancesDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpUtils.methodNotAllowed(exchange);
            return;
        }
        handleGetWorlds(exchange);
    }

    private void handleGetWorlds(HttpExchange exchange) throws IOException {
        CoreLogger.get().info(LOG, "=== SCANNING ALL INSTANCES FOR WORLDS ===");
        try {
            List<WorldMetadata> allWorlds = new ArrayList<>();
            if (!Files.exists(instancesDir)) {
                HttpUtils.ok(exchange, new JsonObject());
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesDir)) {
                for (Path instancePath : stream) {
                    if (!Files.isDirectory(instancePath))
                        continue;
                    Path savesDir = resolveSavesDir(instancePath);
                    if (savesDir != null) {
                        scanSavesFolder(savesDir, allWorlds, instancePath.getFileName().toString());
                    }
                }
            }

            allWorlds.sort(Comparator.comparingLong(WorldMetadata::lastPlayed).reversed());
            JsonObject response = new JsonObject();
            response.add("worlds", gson.toJsonTree(allWorlds));
            HttpUtils.ok(exchange, response);
        } catch (Exception e) {
            CoreLogger.get().error(LOG, "Discovery failed", e);
            HttpUtils.serverError(exchange, e.getMessage());
        }
    }

    private Path resolveSavesDir(Path instanceDir) {
        Path[] candidates = {
                instanceDir.resolve("game").resolve("saves"),
                instanceDir.resolve("saves"),
                instanceDir.resolve(".minecraft").resolve("saves")
        };
        for (Path p : candidates)
            if (Files.isDirectory(p))
                return p;
        return null;
    }

    private void scanSavesFolder(Path savesDir, List<WorldMetadata> worlds, String instanceId) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path worldDir : stream) {
                if (!Files.isDirectory(worldDir))
                    continue;
                WorldMetadata meta = processWorldFolder(worldDir, instanceId);
                if (meta != null)
                    worlds.add(meta);
            }
        } catch (IOException e) {
            CoreLogger.get().error(LOG, "Read error: " + savesDir, e);
        }
    }

    private WorldMetadata processWorldFolder(Path worldDir, String instanceId) throws IOException {
        Path levelDat = worldDir.resolve("level.dat");
        Path levelDatOld = worldDir.resolve("level.dat_old");

        if (!Files.exists(levelDat) && !Files.exists(levelDatOld))
            return null;

        Path target = Files.exists(levelDat) ? levelDat : levelDatOld;
        long lastMod = Files.getLastModifiedTime(target).toMillis();
        String cacheKey = target.toAbsolutePath().toString();

        CachedWorld cached = CACHE.get(cacheKey);
        if (cached != null && cached.lastModified == lastMod)
            return cached.metadata;

        boolean locked = isLocked(worldDir);
        Map<String, Object> nbt = null;
        boolean corrupted = false;

        try {
            if (Files.exists(levelDat)) {
                try (InputStream is = Files.newInputStream(levelDat)) {
                    nbt = new NbtReader(is).parse();
                }
            }
            if (nbt == null && Files.exists(levelDatOld)) {
                try (InputStream is = Files.newInputStream(levelDatOld)) {
                    nbt = new NbtReader(is).parse();
                }
            }
        } catch (Exception e) {
            corrupted = true;
            CoreLogger.get().error(LOG, "Corrupted world: " + worldDir.getFileName());
        }

        if (nbt == null)
            return createFallback(worldDir, instanceId, locked, corrupted);

        Map<String, Object> data = getNested(nbt, "Data");
        if (data == null)
            data = nbt;

        String levelName = getAsString(data.get("LevelName"), worldDir.getFileName().toString());
        long lastPlayed = getAsLong(data.get("LastPlayed"), lastMod);
        int gameType = getAsInt(data.get("GameType"), 0);
        boolean hardcore = getAsBoolean(data.get("hardcore"), false);
        long dayTime = getAsLong(data.get("Time"), 0);

        Long seedVal = getNested(data, "WorldGenSettings.seed");
        long seed = seedVal != null ? seedVal : getAsLong(data.get("RandomSeed"), 0);

        int dataVersion = getAsInt(data.get("DataVersion"), 0);
        String versionName = getAsString(getNested(data, "Version.Name"), inferVersion(dataVersion));
        boolean modded = getAsBoolean(data.get("WasModded"), false);

        String icon = null;
        Path iconP = worldDir.resolve("icon.png");
        if (Files.exists(iconP)) {
            try {
                icon = "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(iconP));
            } catch (IOException ignored) {
            }
        }

        WorldMetadata meta = new WorldMetadata(
                worldDir.getFileName().toString(),
                levelName,
                lastPlayed,
                gameType,
                hardcore,
                versionName,
                dataVersion,
                dayTime,
                seed,
                worldDir.toAbsolutePath().toString(),
                instanceId,
                icon,
                new WorldFlags(locked, corrupted, modded, true));

        CACHE.put(cacheKey, new CachedWorld(meta, lastMod));
        return meta;
    }

    private boolean isLocked(Path worldDir) {
        Path lock = worldDir.resolve("session.lock");
        if (!Files.exists(lock))
            return false;
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.WRITE)) {
            FileLock fl = channel.tryLock();
            if (fl == null)
                return true;
            fl.release();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private WorldMetadata createFallback(Path dir, String instId, boolean locked, boolean corrupted) {
        return new WorldMetadata(
                dir.getFileName().toString(),
                dir.getFileName().toString() + " (Inaccesible)",
                0, 0, false, "Unknown", 0, 0, 0,
                dir.toAbsolutePath().toString(),
                instId, null,
                new WorldFlags(locked, corrupted, false, false));
    }

    private String inferVersion(int dv) {
        if (dv >= 3953)
            return "1.21.x";
        if (dv >= 3463)
            return "1.20.x";
        if (dv >= 3105)
            return "1.19.x";
        if (dv >= 2860)
            return "1.18.x";
        if (dv >= 2724)
            return "1.17.x";
        if (dv >= 2566)
            return "1.16.x";
        if (dv >= 2230)
            return "1.15.x";
        if (dv >= 1976)
            return "1.14.x";
        if (dv >= 1519)
            return "1.13.x";
        if (dv >= 1343)
            return "1.12.x";
        return "Legacy";
    }
}