package dev.novastep.core.minecraft;

import dev.novastep.core.log.CoreLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TelemetryLogger {

    private static final String LOG           = "TelemetryLogger";
    private static final String FOLDER_NAME   = "telemetry_logs";
    private static final int    DRAIN_WAIT_MS = 150;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final Map<String, SessionWriter> writers = new ConcurrentHashMap<>();

    private TelemetryLogger() {}

    public static void open(String launchId, Path logsRoot) {
        Path telemetryDir = logsRoot.resolve(FOLDER_NAME);
        try {
            Files.createDirectories(telemetryDir);
        } catch (IOException ex) {
            CoreLogger.get().warn(LOG, "Cannot create telemetry_logs dir: " + ex.getMessage());
            return;
        }
        Path file = telemetryDir.resolve(launchId + ".telemetry.log");
        SessionWriter writer = new SessionWriter(launchId, file);
        writers.put(launchId, writer);
        writer.start();
        CoreLogger.get().info(LOG, "Telemetry log opened: " + file.getFileName());
    }

    public static void log(String launchId, String category, String message) {
        SessionWriter writer = writers.get(launchId);
        if (writer == null) return;
        String ts   = LocalDateTime.now().format(TS_FMT);
        String line = String.format("[%s] [%s] %s", ts, category.toUpperCase(), message);
        writer.enqueue(line);
    }

    public static void logRam(String launchId, long usedMb, long maxMb) {
        log(launchId, "RAM", "used=" + usedMb + "MB max=" + maxMb + "MB");
    }

    public static void logEvent(String launchId, String event, String detail) {
        log(launchId, "EVENT", event + (detail != null && !detail.isBlank() ? " | " + detail : ""));
    }

    public static void logFps(String launchId, int fps) {
        log(launchId, "FPS", fps + " fps");
    }

    public static void close(String launchId) {
        SessionWriter writer = writers.remove(launchId);
        if (writer != null) writer.close();
    }

    private static final class SessionWriter {

        private final String              launchId;
        private final Path                file;
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(10_000);
        private final AtomicBoolean        closed = new AtomicBoolean(false);
        private Thread                     thread;

        SessionWriter(String launchId, Path file) {
            this.launchId = launchId;
            this.file     = file;
        }

        void start() {
            thread = Thread.ofVirtual()
                    .name("telemetry-writer-" + launchId)
                    .start(this::drainLoop);
        }

        void enqueue(String line) {
            if (!queue.offer(line)) {
                CoreLogger.get().warn(LOG, "[" + launchId + "] Telemetry queue full — entry dropped");
            }
        }

        void close() {
            closed.set(true);
            if (thread != null) thread.interrupt();
            flush();
        }

        private void drainLoop() {
            StringBuilder sb = new StringBuilder(2048);
            while (!closed.get() || !queue.isEmpty()) {
                try {
                    String line = queue.poll(DRAIN_WAIT_MS, TimeUnit.MILLISECONDS);
                    if (line != null) {
                        sb.append(line).append('\n');
                        java.util.List<String> drained = new java.util.ArrayList<>();
                        queue.drainTo(drained, 200);
                        drained.forEach(l -> sb.append(l).append('\n'));
                        write(sb.toString());
                        sb.setLength(0);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            flush();
        }

        private void flush() {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = queue.poll()) != null) sb.append(line).append('\n');
            if (!sb.isEmpty()) write(sb.toString());
        }

        private void write(String content) {
            try {
                Files.writeString(file, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                CoreLogger.get().warn(LOG, "[" + launchId + "] Telemetry write error: " + ex.getMessage());
            }
        }
    }
}
