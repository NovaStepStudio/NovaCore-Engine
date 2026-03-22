package dev.novastep.core.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoreLogger {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static volatile CoreLogger instance;

    private final String launcherName;
    private final Path logDir;
    private final Path logFile;
    private final Level minLevel;

    private final BlockingQueue<String> queue  = new LinkedBlockingQueue<>(8192);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread writer;

    private CoreLogger(String launcherName, Path logDir, Level minLevel) {
        this.launcherName = launcherName;
        this.logDir = logDir;
        this.minLevel = minLevel;

        String date = LocalDate.now().format(DATE_FMT);
        String safeName = launcherName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        this.logFile = logDir.resolve(safeName + "-" + date + ".log");

        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            System.err.println("[CoreLogger] Cannot create log dir: " + e.getMessage());
        }

        this.writer = Thread.ofVirtual().name("log-writer").start(this::drainLoop);
    }

    public static void init(String launcherName, Path logDir, Level minLevel) {
        if (instance == null) {
            synchronized (CoreLogger.class) {
                if (instance == null) {
                    instance = new CoreLogger(launcherName, logDir, minLevel);
                }
            }
        }
    }

    public static CoreLogger get() {
        if (instance == null) {
            init("novacore-engine", Path.of("logs"), Level.INFO);
        }
        return instance;
    }

    public void debug(String module, String msg) { log(Level.DEBUG, module, msg, null); }
    public void info (String module, String msg) { log(Level.INFO,  module, msg, null); }
    public void warn (String module, String msg) { log(Level.WARN,  module, msg, null); }
    public void error(String module, String msg) { log(Level.ERROR, module, msg, null); }
    public void error(String module, String msg, Throwable t) { log(Level.ERROR, module, msg, t); }

    private void log(Level level, String module, String msg, Throwable t) {
        if (level.ordinal() < minLevel.ordinal()) return;
        if (closed.get()) return;

        String ts = LocalDateTime.now().format(TIMESTAMP_FMT);
        String line = String.format("[%s] [%s] [%s] %s", ts, level.name(), module, msg);

        String consoleLine = line;
        String fileLine = line;

        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            fileLine = line + "\n" + sw;
            consoleLine = line + " → " + t.getMessage();
        }

        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(consoleLine);
        } else {
            System.out.println(consoleLine);
        }

        queue.offer(fileLine + "\n");
    }

    public Path getLogFile() { return logFile; }

    public void flush() {
        String line;
        StringBuilder sb = new StringBuilder();
        while ((line = queue.poll()) != null) sb.append(line);
        if (sb.length() > 0) writeToFile(sb.toString());
    }

    public void close() {
        closed.set(true);
        flush();
        writer.interrupt();
    }

    private void drainLoop() {
        StringBuilder sb = new StringBuilder();
        while (!closed.get() || !queue.isEmpty()) {
            try {
                String line = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (line != null) {
                    sb.append(line);
                    java.util.List<String> batch = new java.util.ArrayList<>();
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
            Files.writeString(logFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[CoreLogger] Write failed: " + e.getMessage());
        }
    }
}
