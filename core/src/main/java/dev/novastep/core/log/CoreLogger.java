package dev.novastep.core.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoreLogger {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter TIMESTAMP_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FMT        = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final long MAX_FILE_SIZE    = 10L * 1024 * 1024;
    private static final int  MAX_BACKUPS      = 5;
    private static final int  DRAIN_TIMEOUT_MS = 200;
    private static final int  LOG_KEEP_DAYS    = 14;

    private static final int DEFAULT_QUEUE_LIMIT = 1_500;
    private static final int MIN_QUEUE_LIMIT     = 500;
    private static final int MAX_QUEUE_LIMIT     = 50_000;

    private static volatile CoreLogger instance;

    private final Path          logFile;
    private final Level         minLevel;
    private final BlockingQueue<String> queue;
    private final AtomicBoolean closed       = new AtomicBoolean(false);
    private final AtomicInteger droppedTotal = new AtomicInteger(0);
    private final Thread        writer;

    private CoreLogger(String launcherName, Path logDir, Level minLevel,
                       boolean clearOnStart, int queueLimit) {
        this.minLevel = minLevel;

        int safeLimit = Math.max(MIN_QUEUE_LIMIT, Math.min(MAX_QUEUE_LIMIT, queueLimit));
        this.queue    = new LinkedBlockingQueue<>(safeLimit);

        String date     = LocalDate.now().format(DATE_FMT);
        String safeName = launcherName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        this.logFile    = logDir.resolve(safeName + "-" + date + ".log");

        try { Files.createDirectories(logDir); }
        catch (IOException e) { System.err.println("[CoreLogger] Cannot create log dir: " + e.getMessage()); }

        if (clearOnStart && Files.exists(logFile)) {
            try { Files.delete(logFile); }
            catch (IOException e) { System.err.println("[CoreLogger] Could not clear log file: " + e.getMessage()); }
        }

        cleanOldLogs(logDir, LOG_KEEP_DAYS);

        String separator = String.format(
                "%n[%s] [INFO] [Core] === SESSION START === (pid=%d, queueLimit=%d)%n",
                LocalDateTime.now().format(TIMESTAMP_FMT),
                ProcessHandle.current().pid(),
                safeLimit);
        writeToFile(separator);

        this.writer = Thread.ofVirtual().name("log-writer").start(this::drainLoop);
    }

    public static void init(String launcherName, Path logDir, Level minLevel, boolean clearOnStart) {
        init(launcherName, logDir, minLevel, clearOnStart, DEFAULT_QUEUE_LIMIT);
    }

    public static void init(String launcherName, Path logDir, Level minLevel) {
        init(launcherName, logDir, minLevel, false, DEFAULT_QUEUE_LIMIT);
    }

    public static void init(String launcherName, Path logDir, Level minLevel,
                             boolean clearOnStart, int queueLimit) {
        if (instance == null) {
            synchronized (CoreLogger.class) {
                if (instance == null) {
                    instance = new CoreLogger(launcherName, logDir, minLevel, clearOnStart, queueLimit);
                }
            }
        }
    }

    public static CoreLogger get() {
        if (instance == null) {
            init("novacore-engine", Path.of("logs"), Level.INFO, false, DEFAULT_QUEUE_LIMIT);
        }
        return instance;
    }

    public void debug(String module, String msg)               { log(Level.DEBUG, module, msg, null); }
    public void info (String module, String msg)               { log(Level.INFO,  module, msg, null); }
    public void warn (String module, String msg)               { log(Level.WARN,  module, msg, null); }
    public void error(String module, String msg)               { log(Level.ERROR, module, msg, null); }
    public void error(String module, String msg, Throwable t)  { log(Level.ERROR, module, msg, t);    }

    public Path getLogFile()  { return logFile; }
    public int  getDropped()  { return droppedTotal.get(); }

    public void flush() {
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = queue.poll()) != null) sb.append(line);
        if (!sb.isEmpty()) writeToFile(sb.toString());
    }

    public void close() {
        closed.set(true);
        flush();
        writer.interrupt();
    }

    private void log(Level level, String module, String msg, Throwable t) {
        if (level.ordinal() < minLevel.ordinal()) return;
        if (closed.get()) return;

        String ts   = LocalDateTime.now().format(TIMESTAMP_FMT);
        String line = String.format("[%s] [%s] [%s] %s", ts, level.name(), module, msg);

        String consoleLine, fileLine;
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            fileLine    = line + "\n" + sw;
            consoleLine = line + " -> " + t.getMessage();
        } else {
            fileLine    = line;
            consoleLine = line;
        }

        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(consoleLine);
            System.err.flush();
        } else {
            System.out.println(consoleLine);
            System.out.flush();
        }

        if (!queue.offer(fileLine + "\n")) {
            droppedTotal.incrementAndGet();
            System.err.println("[CoreLogger] Queue full (" + queue.size()
                    + ") — dropped entry for: " + module + " | total dropped: " + droppedTotal.get());
        }
    }

    private void drainLoop() {
        StringBuilder sb = new StringBuilder(4096);
        while (!closed.get() || !queue.isEmpty()) {
            try {
                String line = queue.poll(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (line != null) {
                    sb.append(line);
                    List<String> batch = new ArrayList<>(256);
                    queue.drainTo(batch, 256);
                    batch.forEach(sb::append);
                    writeToFile(sb.toString());
                    sb.setLength(0);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        flush();
    }

    private void writeToFile(String content) {
        try {
            if (Files.exists(logFile) && Files.size(logFile) > MAX_FILE_SIZE) rotateLogs();
            Files.writeString(logFile, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[CoreLogger] Write failed: " + e.getMessage());
        }
    }

    private void rotateLogs() {
        try {
            for (int i = MAX_BACKUPS - 1; i >= 1; i--) {
                Path src = logFile.resolveSibling(logFile.getFileName() + "." + i);
                Path dst = logFile.resolveSibling(logFile.getFileName() + "." + (i + 1));
                if (Files.exists(src)) Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".1"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[CoreLogger] Rotation failed: " + e.getMessage());
        }
    }

    private static void cleanOldLogs(Path logDir, int keepDays) {
        if (!Files.isDirectory(logDir)) return;
        long cutoffMs = System.currentTimeMillis() - ((long) keepDays * 86_400_000L);
        try (var stream = Files.list(logDir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".log") || name.matches(".*\\.log\\.\\d+$");
            }).forEach(p -> {
                try {
                    long lastMod = Files.getLastModifiedTime(p).toMillis();
                    if (lastMod < cutoffMs) {
                        Files.delete(p);
                        System.out.println("[CoreLogger] Cleaned old log: " + p.getFileName());
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException e) {
            System.err.println("[CoreLogger] cleanOldLogs error: " + e.getMessage());
        }
    }
}
