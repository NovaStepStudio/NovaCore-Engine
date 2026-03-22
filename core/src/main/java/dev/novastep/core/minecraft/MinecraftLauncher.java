package dev.novastep.core.minecraft;

import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.manifest.ManifestClient;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.server.LaunchRequest;
import dev.novastep.core.util.SystemResources;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MinecraftLauncher {

    private static final String LOG = "MinecraftLauncher";

    private final ManifestClient   manifestClient;
    private final EventBroadcaster broadcaster;
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final AtomicInteger launchCounter = new AtomicInteger(0);

    public MinecraftLauncher(EventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.manifestClient = new ManifestClient();
    }

    public String launch(LaunchRequest req) {
        String launchId = "launch-" + System.currentTimeMillis() + "-" + launchCounter.incrementAndGet();
        CoreLogger.get().info(LOG, "Launch requested: " + launchId + " v=" + req.version + " user=" + req.resolvedUsername());
        Thread.ofVirtual().name("launch-" + launchId).start(() -> {
            try { runLaunch(launchId, req); }
            catch (Exception ex) {
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
        Path instancePath = Path.of(req.resolvedInstancePath());

        broadcaster.emit("launch_preparing", Map.of("launchId", launchId, "version", req.version));

        VersionInfo versionInfo = manifestClient.fetchVersionById(req.version);

        String javaExec = resolveJavaExecutable(req, instancePath);
        CoreLogger.get().info(LOG, "[" + launchId + "] Java: " + javaExec);
        broadcaster.emitDebug(launchId, "Java: " + javaExec);

        ClasspathBuilder cpBuilder = ClasspathBuilder.fromRequest(req, versionInfo);
        String classpathStr = cpBuilder.buildClasspathString();
        CoreLogger.get().debug(LOG, "[" + launchId + "] Classpath entries: " + cpBuilder.buildClasspathEntries().size());
        broadcaster.emitDebug(launchId, "Classpath entries: " + cpBuilder.buildClasspathEntries().size());

        Path nativesDir = instancePath.resolve("versions").resolve(req.version).resolve("natives");
        boolean nativesExist = false;
        if (Files.isDirectory(nativesDir)) {
            try (var stream = Files.list(nativesDir)) {
                nativesExist = stream.findAny().isPresent();
            } catch (Exception ignored) {}
        }
        if (!nativesExist) {
            broadcaster.emitDebug(launchId, "Nativos no encontrados, extrayendo...");
            nativesDir = cpBuilder.extractNatives();
        }
        CoreLogger.get().debug(LOG, "[" + launchId + "] Natives dir: " + nativesDir);

        Path gameDir = Path.of(req.resolvedGameDir());
        Files.createDirectories(gameDir);
        Files.createDirectories(Path.of(cpBuilder.getAssetsDir()));
        String assetIndex = versionInfo.assetIndex != null ? versionInfo.assetIndex.id : "legacy";

        String launcherName    = req.resolvedLauncherName();
        String launcherVersion = req.resolvedLauncherVersion();

        ArgumentResolver.LaunchContext ctx = new ArgumentResolver.LaunchContext(
            req.resolvedUsername(),    req.version,
            gameDir.toString(),        cpBuilder.getAssetsDir(),
            assetIndex,                req.resolvedUuid(),
            req.resolvedAccessToken(), req.resolvedUserType(),
            req.resolvedClientId(),    req.resolvedXuid(),
            versionInfo.type != null ? versionInfo.type : "release",
            nativesDir.toString(),     classpathStr,
            launcherName,              launcherVersion,
            req.resolvedWidth(),       req.resolvedHeight()
        );
        ArgumentResolver resolver = new ArgumentResolver(versionInfo, ctx).configureFromRequest(req);

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExec);

        cmd.add("-Xms" + req.resolvedMinMemory() + "m");
        cmd.add("-Xmx" + req.resolvedMaxMemory() + "m");

        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dlog4j2.formatMsgNoLookups=true");

        if (req.game != null && req.game.extraJvmProperties != null) {
            req.game.extraJvmProperties.forEach((k, v) -> cmd.add("-D" + k + "=" + v));
        }

        boolean offline = "0".equals(req.resolvedAccessToken()) || "offline".equalsIgnoreCase(req.resolvedUserType());
        if (offline) {
            cmd.add("-Dminecraft.api.env=custom");
            cmd.add("-Dminecraft.api.auth.host=https://invalid.invalid");
            cmd.add("-Dminecraft.api.account.host=https://invalid.invalid");
            cmd.add("-Dminecraft.api.session.host=https://invalid.invalid");
            cmd.add("-Dminecraft.api.services.host=https://invalid.invalid");
            CoreLogger.get().info(LOG, "[" + launchId + "] Offline mode enabled");
            broadcaster.emitDebug(launchId, "Modo offline activado");
        }

        if (req.isAuthlibEnabled()) {
            cmd.add("-javaagent:" + req.authlibInjector.jarPath + "=" + req.authlibInjector.serverUrl);
            broadcaster.emitDebug(launchId, "authlib-injector: " + req.authlibInjector.serverUrl);
        }

        if (req.hardwareAcceleration != null && req.hardwareAcceleration) {
            addHardwareAccelerationFlags(cmd);
            broadcaster.emitDebug(launchId, "Hardware acceleration flags added");
        }

        if (!userSpecifiedGC(req)) {
            String gcPreset = req.gcPreset != null ? req.gcPreset : "auto";
            int maxRam = req.resolvedMaxMemory();
            cmd.addAll(SystemResources.gcFlags(gcPreset, maxRam));
            broadcaster.emitDebug(launchId, "GC preset: " + gcPreset + " (maxRam=" + maxRam + "MB)");
        }

        if (req.jvm != null && req.jvm.prependArgs != null) {
            cmd.addAll(req.jvm.prependArgs);
        }

        if (req.jvm != null && req.jvm.extraArgs != null) {
            cmd.addAll(req.jvm.extraArgs);
        }

        List<String> versionJvmArgs = resolver.resolveJvmArgs();
        Set<String> dedupGC = new HashSet<>();
        boolean skipNext = false;
        for (String arg : versionJvmArgs) {
            if (skipNext) { skipNext = false; continue; }
            if (arg.equals("-cp") || arg.equals("-classpath")) { skipNext = true; continue; }
            if (arg.contains("${classpath}")) continue;
            if (isGcFlag(arg)) {
                if (!dedupGC.add(arg)) continue;
                if (userSpecifiedGC(req)) continue;
            }
            cmd.add(arg);
        }

        cmd.add("-cp");
        cmd.add(classpathStr);
        cmd.add(versionInfo.mainClass);

        List<String> gameArgs = resolver.resolveGameArgs();
        cmd.addAll(gameArgs);

        boolean hasWidth  = cmd.contains("--width");
        boolean hasHeight = cmd.contains("--height");
        if (!hasWidth && !req.isFullscreen()) {
            cmd.add("--width");  cmd.add(String.valueOf(req.resolvedWidth()));
        }
        if (!hasHeight && !req.isFullscreen()) {
            cmd.add("--height"); cmd.add(String.valueOf(req.resolvedHeight()));
        }
        if (req.isFullscreen() && !cmd.contains("--fullscreen")) {
            cmd.add("--fullscreen");
        }

        if (req.game != null && Boolean.TRUE.equals(req.game.disableMultiplayer)) {
            cmd.add("--disableMultiplayer");
        }
        if (req.game != null && Boolean.TRUE.equals(req.game.disableChat)) {
            cmd.add("--disableChat");
        }

        if (req.game != null && req.game.serverHost != null && !req.game.serverHost.isBlank()) {
            cmd.add("--server"); cmd.add(req.game.serverHost);
            if (req.game.serverPort != null) {
                cmd.add("--port"); cmd.add(String.valueOf(req.game.serverPort));
            }
        }

        if (req.game != null && req.game.extraGameArgs != null) {
            cmd.addAll(req.game.extraGameArgs);
        }

        CoreLogger.get().info(LOG, "[" + launchId + "] Command built, mainClass=" + versionInfo.mainClass +
            ", args=" + cmd.size());

        broadcaster.emit("launch_command_ready", Map.of(
            "launchId", launchId,
            "command", cmd,
            "mainClass", versionInfo.mainClass,
            "javaExec", javaExec,
            "offline", offline
        ));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);

        if (!javaExec.equals("java") && !javaExec.equals("java.exe")) {
            try {
                Path javaHome = Path.of(javaExec).getParent();
                if (javaHome != null) {
                    Path parentHome = javaHome.getParent();
                    if (parentHome != null) pb.environment().put("JAVA_HOME", parentHome.toString());
                }
            } catch (Exception ignored) {}
        }

        String gpuPref = req.gpuPreference != null ? req.gpuPreference : "auto";
        if (req.hardwareAcceleration != null && req.hardwareAcceleration) {
            applyHardwareAccelEnv(pb.environment(), gpuPref);
            broadcaster.emitDebug(launchId, "HW accel env vars applied (gpu=" + gpuPref + ")");
        }

        broadcaster.emit("launch_started", Map.of(
            "launchId", launchId,
            "version", req.version,
            "username", req.resolvedUsername(),
            "gameDir", gameDir.toString(),
            "authlib", req.isAuthlibEnabled(),
            "javaExec", javaExec,
            "offline", offline
        ));

        CoreLogger.get().info(LOG, "[" + launchId + "] Process starting: user=" + req.resolvedUsername() +
            " gameDir=" + gameDir);

        Process process = pb.start();
        activeProcesses.put(launchId, process);

        Thread.ofVirtual().name("mc-log-" + launchId).start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    CoreLogger.get().info("MC/" + launchId, line);
                    broadcaster.emit("game_log", Map.of("launchId", launchId, "line", line));
                }
            } catch (Exception ignored) {}
        });

        int exitCode = process.waitFor();
        activeProcesses.remove(launchId);

        CoreLogger.get().info(LOG, "[" + launchId + "] Process exited: code=" + exitCode);
        broadcaster.emit("game_exited", Map.of(
            "launchId", launchId,
            "exitCode", exitCode,
            "status",  exitCode == 0 ? "clean" : "crash"
        ));
    }

    private static void addHardwareAccelerationFlags(List<String> cmd) {
        String os = System.getProperty("os.name", "").toLowerCase();
        cmd.add("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=false");
        cmd.add("-Dorg.lwjgl.util.NoChecks=true");
        cmd.add("-Dorg.lwjgl.system.allocator=rpmalloc");
        if (os.contains("mac")) cmd.add("-XstartOnFirstThread");
    }

    public static void applyHardwareAccelEnv(Map<String, String> env, String gpuPreference) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) {
            if ("dgpu".equals(gpuPreference)) {
                env.put("__NV_PRIME_RENDER_OFFLOAD", "1");
                env.put("__NV_PRIME_RENDER_OFFLOAD_PROVIDER", "NVIDIA-G0");
                env.put("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
                env.put("__VK_LAYER_NV_optimus", "NVIDIA_only");
                env.put("DRI_PRIME", "1");
            } else if ("igpu".equals(gpuPreference)) {
                env.put("DRI_PRIME", "0");
            }
            env.put("MESA_GLSL_CACHE_DISABLE", "false");
        }
    }

    private String resolveJavaExecutable(LaunchRequest req, Path instancePath) {
        String explicit = req.resolvedJavaPath();
        if (explicit != null && !explicit.equals("java") && !explicit.isBlank()) return explicit;

        Path[] runtimeDirs = { req.resolvedRuntimePath(), instancePath.resolve("runtime") };
        for (Path runtimeDir : runtimeDirs) {
            if (!Files.isDirectory(runtimeDir)) continue;
            try (var stream = Files.list(runtimeDir)) {
                var found = stream
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("java-"))
                    .map(p -> RuntimeDownloader.getJavaExecutable(p))
                    .filter(exec -> Files.exists(Path.of(exec)))
                    .findFirst();
                if (found.isPresent()) return found.get();
            } catch (Exception ignored) {}
        }
        return "java";
    }

    private static boolean isGcFlag(String arg) {
        return arg.contains("UseG1GC") || arg.contains("UseZGC") || arg.contains("UseSerialGC")
            || arg.contains("UseParallelGC") || arg.contains("UseShenandoahGC")
            || arg.contains("UseConcMarkSweepGC");
    }

    private static boolean userSpecifiedGC(LaunchRequest req) {
        if (req.jvm == null || req.jvm.extraArgs == null) return false;
        return req.jvm.extraArgs.stream().anyMatch(MinecraftLauncher::isGcFlag);
    }
}
