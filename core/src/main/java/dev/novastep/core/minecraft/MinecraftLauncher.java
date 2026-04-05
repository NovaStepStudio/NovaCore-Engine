package dev.novastep.core.minecraft;

import com.google.gson.Gson;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.manifest.VersionMerger;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.modloader.ModLoaderOrchestrator;
import dev.novastep.core.modloader.ModLoaderProvider;
import dev.novastep.core.modloader.ModLoaderRegistry;
import dev.novastep.core.modloader.model.ExecutionPlan;
import dev.novastep.core.modloader.model.InstalledLoader;
import dev.novastep.core.server.LaunchRequest;
import dev.novastep.core.util.JavaResolver;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MinecraftLauncher {

    private static final String LOG  = "MinecraftLauncher";
    private static final Gson   GSON = new Gson();

    private final EventBroadcaster      broadcaster;
    private final ModLoaderOrchestrator modLoaderOrchestrator;

    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicInteger launchCounter = new AtomicInteger(0);


    public MinecraftLauncher(EventBroadcaster broadcaster, ModLoaderOrchestrator modLoaderOrchestrator) {
        this(broadcaster, modLoaderOrchestrator, null);
    }

    public MinecraftLauncher(EventBroadcaster broadcaster,
                              ModLoaderOrchestrator modLoaderOrchestrator,
                              Path rootDir) {
        this.broadcaster           = broadcaster;
        this.modLoaderOrchestrator = modLoaderOrchestrator;
    }

    public String launch(LaunchRequest req) {
        String launchId = "launch-" + System.currentTimeMillis() + "-" + launchCounter.incrementAndGet();
        CoreLogger.get().info(LOG, "Launch requested: " + launchId
                + " v=" + req.version + " user=" + req.resolvedUsername());

        Thread.ofVirtual().name("launch-" + launchId).start(() -> {
            try {
                runLaunch(launchId, req);
            } catch (Exception ex) {
                String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                CoreLogger.get().error(LOG, "Launch failed: " + launchId + " → " + msg, ex);
                broadcaster.emit("launch_failed", Map.of("launchId", launchId, "error", msg));
            }
        });

        return launchId;
    }

    public boolean kill(String launchId) {
        Process p = activeProcesses.get(launchId);
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            CoreLogger.get().info(LOG, "Process killed: " + launchId);
            return true;
        }
        return false;
    }

    public boolean isRunning(String launchId) {
        Process p = activeProcesses.get(launchId);
        return p != null && p.isAlive();
    }


    private void runLaunch(String launchId, LaunchRequest req) throws Exception {
        Path instancePath = Path.of(req.resolvedInstancePath()).toAbsolutePath();

        broadcaster.emit("launch_preparing", Map.of("launchId", launchId, "version", req.version));

        String vanillaVersionId = resolveVanillaVersionId(req.version, instancePath);
        CoreLogger.get().info(LOG, "[" + launchId + "] Vanilla base: " + vanillaVersionId);

        VersionInfo effectiveInfo = loadLocalVersionInfo(req.version, instancePath);

        String loaderType = detectLoaderType(req.version, vanillaVersionId);
        ExecutionPlan executionPlan = null;

        if (loaderType != null) {
            CoreLogger.get().info(LOG, "[" + launchId + "] Modloader detectado: " + loaderType);
            Optional<ModLoaderProvider> provider = ModLoaderRegistry.get().find(loaderType);
            if (provider.isPresent()) {
                InstalledLoader syntheticLoader = new InstalledLoader();
                syntheticLoader.loaderType       = loaderType;
                syntheticLoader.versionJsonId    = req.version;
                syntheticLoader.minecraftVersion = vanillaVersionId;

                Optional<InstalledLoader> savedState = modLoaderOrchestrator.loadState(instancePath);
                if (savedState.isPresent() && loaderType.equals(savedState.get().loaderType)) {
                    syntheticLoader.versionJsonId = savedState.get().versionJsonId;
                }

                VersionInfo vanillaOnly = loadLocalVersionInfo(vanillaVersionId, instancePath);
                executionPlan = provider.get().buildExecution(
                        syntheticLoader, vanillaOnly, instancePath,
                        req.resolvedLibrariesPath().toAbsolutePath());
            }
        }

        MinecraftVerifier.VerificationResult verification =
                MinecraftVerifier.verify(req, effectiveInfo, vanillaVersionId);
        if (!verification.ok) {
            List<String> missingNames = verification.missing.stream()
                    .map(c -> c.category() + "/" + c.description()).toList();
            broadcaster.emit("launch_verification_failed", Map.of(
                    "launchId", launchId, "missing", missingNames,
                    "hint",     "Re-run install to repair the missing components"));
            CoreLogger.get().warn(LOG, "[" + launchId + "] Pre-launch check FAILED — "
                    + verification.missing.size() + " component(s) missing: " + missingNames);
            throw new IllegalStateException("Pre-launch verification failed — missing: " + missingNames);
        }

        String javaExec = resolveJavaExecutable(req, instancePath);
        CoreLogger.get().info(LOG, "[" + launchId + "] Java: " + javaExec);

        ArgumentResolver argResolver = ArgumentResolver.fromRequest(req, effectiveInfo, instancePath, vanillaVersionId);
        ClasspathBuilder cpBuilder = ClasspathBuilder.fromRequest(req, effectiveInfo, vanillaVersionId);

        if (executionPlan != null && !executionPlan.additionalClasspath.isEmpty()) {
            cpBuilder.appendModloaderEntries(executionPlan.additionalClasspath);
        }

        String mainClass = effectiveInfo.mainClass;
        if (executionPlan != null && executionPlan.mainClass != null) {
            mainClass = executionPlan.mainClass;
        }

        List<String> command = buildCommand(javaExec, mainClass, argResolver, cpBuilder);

        CoreLogger.get().info(LOG, "[" + launchId + "] Launching: mainClass=" + mainClass + " args=" + command.size());

        broadcaster.emit("launch_starting", Map.of(
                "launchId",  launchId,
                "mainClass", mainClass,
                "version",   req.version));

        Path workDir = instancePath.resolve("game");
        Files.createDirectories(workDir);

        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(false)
                .start();

        activeProcesses.put(launchId, process);
        broadcaster.emit("launch_started", Map.of("launchId", launchId, "pid", process.pid()));
        CoreLogger.get().info(LOG, "[" + launchId + "] PID=" + process.pid());

        Path logRoot = CoreLogger.get().getLogFile().getParent();
        GameLogManager gameLogs = GameLogManager.openOrNull(logRoot, launchId);
        broadcaster.emit("launch_log_file", Map.of(
                "launchId", launchId,
                "logFile",  gameLogs.getLogFile().toAbsolutePath().toString()));

        streamOutput(launchId, process, gameLogs);

        int exitCode = process.waitFor();
        activeProcesses.remove(launchId);
        gameLogs.close();

        CoreLogger.get().info(LOG, "[" + launchId + "] Exited: " + exitCode);
        broadcaster.emit("launch_exited", Map.of("launchId", launchId, "exitCode", exitCode));
    }

    private String resolveVanillaVersionId(String versionId, Path instancePath) throws IOException {
        Path versionFile = instancePath.resolve("versions")
                .resolve(versionId).resolve(versionId + ".json");
        if (!Files.exists(versionFile)) return versionId;

        VersionInfo raw = GSON.fromJson(
                Files.readString(versionFile, StandardCharsets.UTF_8), VersionInfo.class);
        if (raw.inheritsFrom != null && !raw.inheritsFrom.isBlank()) {
            return resolveVanillaVersionId(raw.inheritsFrom, instancePath);
        }
        return versionId;
    }

    private VersionInfo loadLocalVersionInfo(String versionId, Path instancePath) throws IOException {
        Path versionFile = instancePath.resolve("versions")
                .resolve(versionId).resolve(versionId + ".json");
        if (!Files.exists(versionFile)) {
            throw new IOException("Version JSON not found: " + versionFile + " — run install first.");
        }
        VersionInfo info = GSON.fromJson(
                Files.readString(versionFile, StandardCharsets.UTF_8), VersionInfo.class);
        if (info.inheritsFrom != null && !info.inheritsFrom.isBlank()) {
            VersionInfo parent = loadLocalVersionInfo(info.inheritsFrom, instancePath);
            info = VersionMerger.merge(parent, info);
        }
        return info;
    }

    private static String detectLoaderType(String versionId, String vanillaVersionId) {
        if (versionId.equals(vanillaVersionId)) return null;
        String lower = versionId.toLowerCase();

        if (lower.startsWith("legacyfabric-")) return "legacyfabric";
        if (lower.startsWith("fabric-")) return "fabric";
        if (lower.startsWith("quilt-")) return "quilt";
        if (lower.startsWith("neoforge-")) return "neoforge";
        if (lower.startsWith("forge-") || lower.contains("-forge-")) return "forge";
        if (lower.contains("optifine") || lower.contains("hd_u_")) return "optifine";

        return null;
    }

    private List<String> buildCommand(
            String javaExec, String mainClass,
            ArgumentResolver argResolver, ClasspathBuilder cpBuilder) {

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExec);
        cmd.addAll(argResolver.buildJvmArgs(cpBuilder));
        cmd.add(mainClass);
        cmd.addAll(argResolver.buildGameArgs());
        return cmd;
    }

    private void streamOutput(String launchId, Process process, GameLogManager gameLogs) {
        Thread.ofVirtual().name("stdout-" + launchId).start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    gameLogs.log("stdout", line);
                    broadcaster.emit("game_stdout", Map.of("launchId", launchId, "line", line));
                    broadcaster.emit("game_log",    Map.of("launchId", launchId, "line", line, "stream", "stdout"));
                }
            } catch (IOException ex) {
                CoreLogger.get().warn(LOG, "[" + launchId + "] stdout stream cerrado: " + ex.getMessage());
                gameLogs.log("system", "stdout stream error: " + ex.getMessage());
            }
        });

        Thread.ofVirtual().name("stderr-" + launchId).start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    gameLogs.log("stderr", line);
                    broadcaster.emit("game_stderr", Map.of("launchId", launchId, "line", line));
                    broadcaster.emit("game_log",    Map.of("launchId", launchId, "line", line, "stream", "stderr"));
                }
            } catch (IOException ex) {
                CoreLogger.get().warn(LOG, "[" + launchId + "] stderr stream cerrado: " + ex.getMessage());
                gameLogs.log("system", "stderr stream error: " + ex.getMessage());
            }
        });
    }

    private String resolveJavaExecutable(LaunchRequest req, Path instancePath) {
        if (req.javaPath != null && !req.javaPath.isBlank() && !req.javaPath.equals("java")) {
            return req.javaPath;
        }
        Path sharedPath = (req.sharedPath != null && !req.sharedPath.isBlank())
                ? Path.of(req.sharedPath).toAbsolutePath() : null;

        String fromRuntime = RuntimeDownloader.findExistingRuntime(instancePath, sharedPath);
        if (fromRuntime != null && Files.isRegularFile(Path.of(fromRuntime))) {
            return fromRuntime;
        }

        return JavaResolver.resolve(instancePath, sharedPath);
    }
}
