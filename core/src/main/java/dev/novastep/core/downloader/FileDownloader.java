package dev.novastep.core.downloader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;

import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.websocket.EventBroadcaster;

public class FileDownloader {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long REPORT_INTERVAL= 128 * 1024;
    private static final int READ_TIMEOUT = 60;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1500;
    private static final String LOG = "FileDownloader";

    private final HttpClient http;
    private final EventBroadcaster broadcaster;
    private final DownloadSession session;

    public FileDownloader(HttpClient http, EventBroadcaster broadcaster, DownloadSession session) {
        this.http = http;
        this.broadcaster = broadcaster;
        this.session = session;
    }

    public DownloadResult download(DownloadTask task) {
        if (Files.exists(task.destination)) {
            if (task.sha1 != null) {
                try {
                    if (Sha1Verifier.verifyFile(task.destination, task.sha1)) {
                        broadcaster.emitDownloadComplete(task.sessionId, task.category, task.name, task.expectedSize, true);
                        return DownloadResult.skipped(task);
                    }
                } catch (IOException ignored) {}
            } else {
                broadcaster.emitDownloadComplete(task.sessionId, task.category, task.name, task.expectedSize, true);
                return DownloadResult.skipped(task);
            }
        }

        try { Files.createDirectories(task.destination.getParent()); }
        catch (IOException e) { return DownloadResult.failure(task, "mkdir: " + e.getMessage()); }

        broadcaster.emitDownloadStart(task.sessionId, task.category, task.name, task.expectedSize);

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return attemptDownload(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return DownloadResult.failure(task, "Interrupted");
            } catch (Exception e) {
                lastError = e;
                CoreLogger.get().warn(LOG, "Attempt " + attempt + "/" + MAX_RETRIES + " failed for " +
                    task.name + ": " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return DownloadResult.failure(task, "Interrupted");
                    }
                    try { Files.deleteIfExists(task.destination); } catch (IOException ignored) {}
                }
            }
        }

        String msg = lastError != null ? lastError.getClass().getSimpleName() + ": " + lastError.getMessage() : "Unknown";
        CoreLogger.get().error(LOG, "Download failed: " + task.name + " → " + msg);
        broadcaster.emitDownloadError(task.sessionId, task.category, task.name, msg);
        return DownloadResult.failure(task, msg);
    }

    private DownloadResult attemptDownload(DownloadTask task) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(task.url))
            .timeout(Duration.ofSeconds(READ_TIMEOUT))
            .header("User-Agent", "novacore-engine/1.0 (NovaStepStudios)")
            .GET().build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200)
            throw new IOException("HTTP " + response.statusCode() + " → " + task.url);

        MessageDigest digest = task.sha1 != null ? Sha1Verifier.newDigest() : null;
        long written = 0L;
        long lastReported = 0L;
        byte[] buf = new byte[BUFFER_SIZE];

        try (InputStream in = new BufferedInputStream(response.body(), BUFFER_SIZE);
             FileChannel out = FileChannel.open(task.destination,
                 StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            int read;
            while ((read = in.read(buf)) != -1) {
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, read);
                while (bb.hasRemaining()) out.write(bb);
                if (digest != null) digest.update(buf, 0, read);
                written += read;
                session.addDownloadedBytes(task, read);
                if (written - lastReported >= REPORT_INTERVAL) {
                    broadcaster.emitDownloadProgress(task.sessionId, task.category, task.name, written, task.expectedSize);
                    lastReported = written;
                }
            }
        }

        boolean sha1Ok = true;
        if (digest != null && task.sha1 != null) {
            String computed = Sha1Verifier.finalize(digest);
            sha1Ok = Sha1Verifier.matches(computed, task.sha1);
            broadcaster.emitSha1Check(task.sessionId, task.name, sha1Ok, task.sha1, computed);
            if (!sha1Ok) {
                try { Files.deleteIfExists(task.destination); } catch (IOException ignored) {}
                throw new IOException("SHA-1 mismatch: " + task.name + " expected=" + task.sha1 + " got=" + computed);
            }
        }

        broadcaster.emitDownloadComplete(task.sessionId, task.category, task.name, written, false);
        return DownloadResult.success(task, written, sha1Ok);
    }
}
