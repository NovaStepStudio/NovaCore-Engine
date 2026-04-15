package dev.novastep.core.minecraft;

import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.websocket.EventBroadcaster;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TelemetryManager {

    private static final String LOG = "TelemetryManager";

    private static final Pattern TELEMETRY_LOGGER = Pattern.compile(
            "^\\[\\d{2}:\\d{2}:\\d{2}\\]\\s+\\[(?:Telemetry[^/]*/|TelemetryManager/)[^]]+\\]:\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENDING_EVENT = Pattern.compile(
            "(?:Sending|Queuing|Logging)\\s+event[:\\s]+([\\w]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PERF_METRIC = Pattern.compile(
            "(?:fps|tps|tick|memory|ram|heap|gc)[\\s:=]+([\\d.]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WORLD_JOIN = Pattern.compile(
            "(?:Joining|Connecting to|Loading level|WorldLoaded)[:\\s]+(.+)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WORLD_LOADED_EVENT = Pattern.compile(
            "worldLoaded|world_loaded|WorldLoaded",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CRASH_REPORT      = Pattern.compile("---- Minecraft Crash Report ----");
    private static final Pattern CRASH_DESCRIPTION = Pattern.compile("^// (.+)$");
    private static final Pattern FPS_LINE          = Pattern.compile("(\\d+)\\s+fps", Pattern.CASE_INSENSITIVE);

    private static final Map<String, CrashCollector> crashCollectors = new ConcurrentHashMap<>();
    private static final Map<String, ScheduledFuture<?>> ramTasks   = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService RAM_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "telemetry-ram");
                t.setDaemon(true);
                return t;
            });

    private TelemetryManager() {}

    public static void intercept(String launchId, String rawLine, EventBroadcaster broadcaster) {
        if (rawLine == null || rawLine.isBlank()) return;
        if (interceptCrash(launchId, rawLine, broadcaster)) return;

        Matcher telLine = TELEMETRY_LOGGER.matcher(rawLine);
        if (telLine.matches()) {
            interceptTelemetryPayload(launchId, rawLine, telLine.group(1), broadcaster);
            return;
        }

        GameLogManager.ParsedLogLine parsed = GameLogManager.parseLine(rawLine);
        if ("INFO".equals(parsed.level) || "DEBUG".equals(parsed.level)) {
            interceptTelemetryPayload(launchId, rawLine, parsed.message, broadcaster);
        }

        Matcher fps = FPS_LINE.matcher(rawLine);
        if (fps.find()) {
            try {
                int fpsValue = Integer.parseInt(fps.group(1));
                broadcaster.emit("telemetry_fps", Map.of(
                        "launchId", launchId,
                        "fps",      fpsValue,
                        "raw",      rawLine));
                TelemetryLogger.logFps(launchId, fpsValue);
            } catch (NumberFormatException ignored) {}
        }
    }

    public static void startRamTelemetry(String launchId, EventBroadcaster broadcaster,
                                          int intervalSeconds) {
        if (intervalSeconds <= 0) intervalSeconds = 30;
        final int interval = intervalSeconds;

        ScheduledFuture<?> task = RAM_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                Runtime rt        = Runtime.getRuntime();
                long heapUsedMb   = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long heapTotalMb  = rt.totalMemory() / (1024 * 1024);
                long systemFreeMb  = dev.novastep.core.util.SystemResources.estimatedFreeRamMb();
                long systemTotalMb = dev.novastep.core.util.SystemResources.totalSystemRamMb();

                broadcaster.emit("telemetry", Map.of(
                        "launchId",        launchId,
                        "ramMb",           heapUsedMb,
                        "heapTotalMb",     heapTotalMb,
                        "systemFreeMb",    systemFreeMb,
                        "systemTotalMb",   systemTotalMb,
                        "intervalSeconds", interval));

                TelemetryLogger.logRam(launchId, heapUsedMb, heapTotalMb);
            } catch (Exception ex) {
                CoreLogger.get().debug(LOG, "[" + launchId + "] RAM telemetry error: " + ex.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);

        ramTasks.put(launchId, task);
        CoreLogger.get().debug(LOG, "[" + launchId + "] RAM telemetry started (interval=" + interval + "s)");
    }

    public static void openTelemetryLog(String launchId, Path logsRoot) {
        TelemetryLogger.open(launchId, logsRoot);
    }

    public static void cleanup(String launchId) {
        ScheduledFuture<?> task = ramTasks.remove(launchId);
        if (task != null) {
            task.cancel(false);
            CoreLogger.get().debug(LOG, "[" + launchId + "] RAM telemetry stopped");
        }
        crashCollectors.remove(launchId);
        TelemetryLogger.close(launchId);
    }

    private static class CrashCollector {
        boolean      active      = false;
        final List<String> lines = new ArrayList<>();
        String       description = null;

        void reset() {
            active = false;
            lines.clear();
            description = null;
        }
    }

    private static void interceptTelemetryPayload(String launchId, String rawLine,
                                                   String payload, EventBroadcaster broadcaster) {
        Matcher evMatch = SENDING_EVENT.matcher(payload);
        if (evMatch.find()) {
            String eventName = evMatch.group(1);
            broadcaster.emit("telemetry_event", Map.of(
                    "launchId", launchId,
                    "event",    eventName,
                    "raw",      rawLine));
            TelemetryLogger.logEvent(launchId, eventName, null);

            if (WORLD_LOADED_EVENT.matcher(eventName).find()) {
                broadcaster.emit("telemetry_world_loaded", Map.of(
                        "launchId", launchId,
                        "raw",      rawLine));
                TelemetryLogger.logEvent(launchId, "world_loaded", null);
            }
            return;
        }

        Matcher wj = WORLD_JOIN.matcher(payload);
        if (wj.find()) {
            String detail = wj.group(1) != null ? wj.group(1).trim() : "";
            broadcaster.emit("telemetry_world_join", Map.of(
                    "launchId", launchId,
                    "detail",   detail,
                    "raw",      rawLine));
            TelemetryLogger.logEvent(launchId, "world_join", detail);
            return;
        }

        Matcher pm = PERF_METRIC.matcher(payload);
        if (pm.find()) {
            broadcaster.emit("telemetry_perf", Map.of(
                    "launchId", launchId,
                    "metric",   pm.group(0).trim(),
                    "value",    pm.group(1),
                    "raw",      rawLine));
        }
    }

    private static boolean interceptCrash(String launchId, String rawLine, EventBroadcaster broadcaster) {
        CrashCollector collector = crashCollectors.computeIfAbsent(launchId, k -> new CrashCollector());

        if (CRASH_REPORT.matcher(rawLine).find()) {
            collector.reset();
            collector.active = true;
            collector.lines.add(rawLine);
            broadcaster.emit("game_crash_report_start", Map.of("launchId", launchId));
            TelemetryLogger.logEvent(launchId, "crash_report_start", null);
            return true;
        }

        if (!collector.active) return false;

        collector.lines.add(rawLine);

        if (collector.description == null) {
            Matcher dm = CRASH_DESCRIPTION.matcher(rawLine.trim());
            if (dm.matches()) collector.description = dm.group(1);
        }

        if (collector.lines.size() >= 200 || rawLine.contains("-- System Details --")) {
            String fullReport   = String.join("\n", collector.lines);
            String description  = collector.description != null ? collector.description : "Unknown crash";
            broadcaster.emit("game_crash_report", Map.of(
                    "launchId",    launchId,
                    "description", description,
                    "report",      fullReport));
            TelemetryLogger.logEvent(launchId, "crash_report_complete", description);
            collector.reset();
        }

        return true;
    }
}
