package dev.novastep.core.minecraft;

import dev.novastep.core.log.CoreLogger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameLogManager {

    private static final String LOG_PREFIX = "GameLogManager";
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Path logFile;
    private final PrintWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private GameLogManager(Path logFile, PrintWriter writer) {
        this.logFile = logFile;
        this.writer = writer;
    }

    public static GameLogManager open(Path rootDir, String launchId) throws IOException {
        Path logsDir = rootDir.resolve("logs-game"); 
        Files.createDirectories(logsDir);

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String safeId  = launchId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        Path   logFile = logsDir.resolve("game-" + safeId + "-" + timestamp + ".log");

        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new FileWriter(logFile.toFile(), true)),
                false   
        );

        GameLogManager mgr = new GameLogManager(logFile, writer);
        mgr.writeHeader(launchId);

        CoreLogger.get().info(LOG_PREFIX, "Sesión de log iniciada: " + logFile);
        return mgr;
    }

    public static GameLogManager openOrNull(Path rootDir, String launchId) {
        try {
            return open(rootDir, launchId);
        } catch (IOException ex) {
            CoreLogger.get().warn(LOG_PREFIX,
                    "No se pudo crear el archivo de log para " + launchId + ": " + ex.getMessage());
            return new NullGameLogManager();
        }
    }

    public synchronized void log(String stream, String line) {
        if (closed.get()) return;
        String ts = LocalDateTime.now().format(LINE_FMT);
        writer.println("[" + ts + "] [" + stream.toUpperCase() + "] " + line);
        writer.flush(); 
    }

    public Path getLogFile() { return logFile; }

    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            writeFooter();
            writer.flush();
            writer.close();
            CoreLogger.get().info(LOG_PREFIX, "Sesión de log cerrada: " + logFile.getFileName());
        }
    }

    private void writeHeader(String launchId) {
        String border = "=".repeat(70);
        writer.println(border);
        writer.println("  NovaCore-Engine — Game Log");
        writer.println("  Launch ID : " + launchId);
        writer.println("  Started   : " + LocalDateTime.now());
        writer.println(border);
        writer.println();
        writer.flush();
    }

    private void writeFooter() {
        writer.println();
        String border = "-".repeat(70);
        writer.println(border);
        writer.println("  Log cerrado: " + LocalDateTime.now());
        writer.println(border);
    }

    private static final class NullGameLogManager extends GameLogManager {
        private static final OutputStream DEV_NULL = new OutputStream() {
            @Override public void write(int b) { /* no-op */ }
            @Override public void write(byte[] b, int off, int len) { /* no-op */ }
        };

        NullGameLogManager() {
            super(Path.of("/dev/null"), new PrintWriter(DEV_NULL, false));
        }

        @Override
        public synchronized void log(String stream, String line) { /* no-op */ }

        @Override
        public synchronized void close() { /* no-op */ }
    }
}