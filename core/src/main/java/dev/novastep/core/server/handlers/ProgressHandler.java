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
        if (HttpUtils.handleCors(exchange)) return;
        if (!HttpUtils.requireMethod(exchange, "GET")) return;

        String sessionId = HttpUtils.queryParam(exchange, "sessionId");

        if (sessionId == null || sessionId.isBlank()) {
            List<Map<String, Object>> snapshots = new ArrayList<>();
            for (DownloadSession session : downloadManager.getAllSessions()) {
                snapshots.add(session.toSnapshot());
            }
            HttpUtils.ok(exchange, Map.of(
                "count", snapshots.size(),
                "sessions", snapshots
            ));
            return;
        }

        Optional<DownloadSession> sessionOpt = downloadManager.getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            HttpUtils.notFound(exchange, "Session not found: " + sessionId);
            return;
        }

        HttpUtils.ok(exchange, sessionOpt.get().toSnapshot());
    }
}