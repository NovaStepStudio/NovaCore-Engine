package dev.novastep.core.server.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.novastep.core.downloader.DownloadManager;
import dev.novastep.core.downloader.DownloadSession;
import dev.novastep.core.server.HttpUtils;

import java.io.IOException;
import java.util.*;

public class ProgressHandler implements HttpHandler {

    private final DownloadManager downloadManager;

    public ProgressHandler(DownloadManager downloadManager) {
        this.downloadManager = downloadManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtils.handleCors(exchange))
            return;
        if (!HttpUtils.requireMethod(exchange, "GET"))
            return;

        String path = exchange.getRequestURI().getPath();

        if (path.equals("/progress/summary")) {
            handleSummary(exchange);
            return;
        }

        handleSessionProgress(exchange);
    }

    private void handleSessionProgress(HttpExchange exchange) throws IOException {
        String sessionId = HttpUtils.queryParam(exchange, "sessionId");

        if (sessionId == null || sessionId.isBlank()) {
            List<Map<String, Object>> snapshots = new ArrayList<>();
            for (DownloadSession session : downloadManager.getAllSessions()) {
                snapshots.add(session.toSnapshot());
            }
            HttpUtils.ok(exchange, Map.of(
                    "count", snapshots.size(),
                    "sessions", snapshots));
            return;
        }

        Optional<DownloadSession> sessionOpt = downloadManager.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            HttpUtils.notFound(exchange, "Session not found: " + sessionId);
            return;
        }

        HttpUtils.ok(exchange, sessionOpt.get().toSnapshot());
    }

    private void handleSummary(HttpExchange exchange) throws IOException {
        Collection<DownloadSession> sessions = downloadManager.getAllSessions();

        long totalBytes = 0;
        long downloadedBytes = 0;
        int totalFiles = 0;
        int doneFiles = 0;

        Map<String, Boolean> categories = new LinkedHashMap<>();
        categories.put("client", false);
        categories.put("libraries", false);
        categories.put("assets", false);
        categories.put("natives", false);
        categories.put("jvm", false);

        for (DownloadSession s : sessions) {
            Map<String, Object> snap = s.toSnapshot();
            totalBytes += (long) snap.getOrDefault("totalBytes", 0L);
            downloadedBytes += (long) snap.getOrDefault("downloadedBytes", 0L);
            totalFiles += (int) snap.getOrDefault("totalFiles", 0);
            doneFiles += (int) snap.getOrDefault("completedFiles", 0);
            doneFiles += (int) snap.getOrDefault("skippedFiles", 0);

            for (String cat : categories.keySet()) {
                if (!s.getFilesByCategory(cat).isEmpty()) {
                    categories.put(cat, true);
                }
            }
        }

        int percent = totalBytes > 0 ? (int) (downloadedBytes * 100L / totalBytes) : 0;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("overallPercent", percent);
        resp.put("totalBytes", totalBytes);
        resp.put("downloadedBytes", downloadedBytes);
        resp.put("totalFiles", totalFiles);
        resp.put("doneFiles", doneFiles);
        resp.put("activeSessions", sessions.size());
        resp.put("coreParts", categories);
        resp.put("message", sessions.isEmpty() ? "No active sessions" : "Installation in progress");

        HttpUtils.ok(exchange, resp);
    }
}