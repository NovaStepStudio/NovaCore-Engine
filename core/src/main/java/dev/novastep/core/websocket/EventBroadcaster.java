package dev.novastep.core.websocket;

import com.google.gson.Gson;
import dev.novastep.core.log.CoreLogger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class EventBroadcaster extends WebSocketServer {

    private static final Gson   GSON = new Gson();
    private static final String LOG  = "EventBroadcaster";

    private final String accessToken;
    private volatile dev.novastep.core.downloader.DownloadManager downloadManager = null;

    public EventBroadcaster(int port, String accessToken) {
        super(new InetSocketAddress("localhost", port));
        this.accessToken = accessToken;
        setReuseAddr(true);
        setConnectionLostTimeout(60);
    }

    public void setDownloadManager(dev.novastep.core.downloader.DownloadManager dm) {
        this.downloadManager = dm;
    }

    @Override
    public void onStart() {
        CoreLogger.get().info(LOG, "WebSocket started on port " + getPort());
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String token = extractQueryParam(handshake.getResourceDescriptor(), "token");

        if (token == null || token.isBlank()) {
            token = handshake.getFieldValue("X-Access-Token");
        }

        if (!accessToken.equals(token)) {
            CoreLogger.get().warn(LOG, "WS rechazado — token inválido desde " + conn.getRemoteSocketAddress());
            conn.close(1008, "Unauthorized");
            return;
        }

        CoreLogger.get().info(LOG, "WS cliente autenticado: " + conn.getRemoteSocketAddress());
        sendTo(conn, "connected", map(
                "message", "novacore-engine ready",
                "version", dev.novastep.core.CoreVersion.get()));

        if (downloadManager != null) {
            java.util.List<java.util.Map<String, Object>> snapshots = downloadManager.getRecoverySnapshots();
            if (!snapshots.isEmpty()) {
                emitRecoveryState(conn, snapshots);
                CoreLogger.get().info(LOG, "Recovery state enviado: " + snapshots.size() + " sesiones activas");
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        CoreLogger.get().info(LOG, "WS cliente desconectado: " + conn.getRemoteSocketAddress()
            + " (code=" + code + ")");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {}

    @Override
    public void onError(WebSocket conn, Exception ex) {
        CoreLogger.get().error(LOG, "WebSocket error: " + ex.getMessage(), ex);
    }

    public void emit(String eventType, Object payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", eventType);
        envelope.put("data",  payload);
        broadcast(GSON.toJson(envelope));
    }

    public void emitDownloadStart(String sessionId, String category, String filename, long totalBytes) {
        emit("download_start", map("sessionId", sessionId, "category", category, "file", filename, "total", totalBytes));
    }

    public void emitDownloadProgress(String sessionId, String category, String filename, long downloaded, long total) {
        int percent = total > 0 ? (int) (downloaded * 100L / total) : 0;
        emit("download_progress", map(
            "sessionId", sessionId, "category", category, "file", filename,
            "downloaded", downloaded, "total", total, "percent", percent));
    }

    public void emitDownloadComplete(String sessionId, String category, String filename, long bytes, boolean skipped) {
        emit("download_complete", map(
            "sessionId", sessionId, "category", category, "file", filename,
            "bytes", bytes, "skipped", skipped));
    }

    public void emitDownloadError(String sessionId, String category, String filename, String error) {
        CoreLogger.get().error(LOG, "Download error [" + sessionId + "] " + filename + ": " + error);
        emit("download_error", map("sessionId", sessionId, "category", category, "file", filename, "error", error));
    }

    public void emitSessionProgress(String sessionId, int completed, int skipped, int total, int percent,
                                    long downloadedBytes, long totalBytes) {
        emit("session_progress", map(
            "sessionId", sessionId, "completedFiles", completed, "skippedFiles", skipped,
            "totalFiles", total, "overallPercent", percent,
            "downloadedBytes", downloadedBytes, "totalBytes", totalBytes));
    }

    public void emitSessionCompleted(String sessionId, int totalFiles, long totalBytes) {
        CoreLogger.get().info(LOG, "Session completed: " + sessionId + " files=" + totalFiles);
        emit("session_completed", map("sessionId", sessionId, "totalFiles", totalFiles, "totalBytes", totalBytes));
    }

    public void emitSessionFailed(String sessionId, String reason) {
        CoreLogger.get().error(LOG, "Session failed: " + sessionId + " reason=" + reason);
        emit("session_failed", map("sessionId", sessionId, "reason", reason));
    }

    public void emitSha1Check(String sessionId, String filename, boolean passed, String expected, String computed) {
        if (!passed) CoreLogger.get().warn(LOG, "SHA1 mismatch [" + sessionId + "] " + filename);
        emit("sha1_check", map(
            "sessionId", sessionId, "file", filename,
            "passed", passed, "expected", expected, "computed", computed));
    }

    public void emitManifestResolved(String sessionId, String version) {
        CoreLogger.get().info(LOG, "Manifest resolved [" + sessionId + "]: " + version);
        emit("manifest_resolved", map("sessionId", sessionId, "version", version));
    }

    public void emitDebug(String sessionId, String message) {
        CoreLogger.get().debug(LOG, "[" + sessionId + "] " + message);
        emit("debug", map("sessionId", sessionId, "message", message));
    }

    public void emitGameLogWarn(String launchId, String line, String logger) {
        emit("game_log_warn", map("launchId", launchId, "line", line, "logger", logger));
    }

    public void emitGameLogError(String launchId, String line, String logger) {
        emit("game_log_error", map("launchId", launchId, "line", line, "logger", logger));
    }

    public void emitGameLogFatal(String launchId, String line, String logger) {
        emit("game_log_fatal", map("launchId", launchId, "line", line, "logger", logger));
    }

    public void emitGameCrash(String launchId, int exitCode, String reason) {
        CoreLogger.get().error(LOG, "Game crash [" + launchId + "] exitCode=" + exitCode + " reason=" + reason);
        emit("game_crash", map("launchId", launchId, "exitCode", exitCode, "reason", reason));
    }

    public void emitInstanceStarted(String launchId, String version, long pid) {
        emit("instance_started", map("launchId", launchId, "version", version, "pid", pid));
    }

    public void emitInstanceStopped(String launchId, int exitCode, boolean normal, long durationMs) {
        emit("instance_stopped", map(
            "launchId", launchId, "exitCode", exitCode,
            "normal", normal, "durationMs", durationMs));
    }

    public void emitRecoveryState(org.java_websocket.WebSocket conn,
                                   java.util.List<java.util.Map<String, Object>> snapshots) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", "recovery_state");
        envelope.put("data",  Map.of("snapshots", snapshots, "count", snapshots.size()));
        conn.send(GSON.toJson(envelope));
    }

    private void sendTo(WebSocket conn, String eventType, Object payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event", eventType);
        envelope.put("data",  payload);
        conn.send(GSON.toJson(envelope));
    }

    private static String extractQueryParam(String resource, String key) {
        if (resource == null) return null;
        int q = resource.indexOf('?');
        if (q < 0) return null;
        String query = resource.substring(q + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = pair.substring(0, eq);
            if (k.equals(key)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static Map<String, Object> map(Object... kvPairs) {
        if (kvPairs.length % 2 != 0) throw new IllegalArgumentException("Deben ser pares clave-valor");
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) m.put((String) kvPairs[i], kvPairs[i + 1]);
        return m;
    }
}
