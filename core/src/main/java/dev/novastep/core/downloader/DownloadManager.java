package dev.novastep.core.downloader;
import dev.novastep.core.downloader.model.DownloadResult;
import dev.novastep.core.downloader.model.DownloadTask;

import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.util.SystemResources;
import dev.novastep.core.websocket.EventBroadcaster;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadManager {

    private static final int    HTTP_CONNECT_TIMEOUT = 15;
    private static final String LOG = "DownloadManager";

    private static final int HTTP_CONCURRENCY_MULTIPLIER = 2;

    private final int              maxThreads;
    private final EventBroadcaster broadcaster;
    private final ExecutorService  pool;
    private final HttpClient       http;
    private final java.util.concurrent.Semaphore httpSemaphore;

    private final DownloadPriority priority;
    private final AtomicInteger    sessionCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<String, DownloadSession> sessions = new ConcurrentHashMap<>();

    public DownloadManager(int maxThreads, EventBroadcaster broadcaster) {
        this.maxThreads  = SystemResources.safeThreads(maxThreads);
        this.broadcaster = broadcaster;

        int httpConcurrency = Math.min(
                this.maxThreads * HTTP_CONCURRENCY_MULTIPLIER,
                SystemResources.MAX_DOWNLOAD_THREADS * HTTP_CONCURRENCY_MULTIPLIER);
        this.httpSemaphore = new java.util.concurrent.Semaphore(httpConcurrency, true);
        this.priority      = new DownloadPriority(this.maxThreads);

        this.pool = new ThreadPoolExecutor(
                this.maxThreads, this.maxThreads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "mc-dl-" + sessionCounter.get());
                    t.setDaemon(true);
                    return t;
                }
        );

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .executor(Executors.newFixedThreadPool(
                        Math.max(4, this.maxThreads / 2),
                        r -> {
                            Thread t = new Thread(r, "mc-http-io");
                            t.setDaemon(true);
                            return t;
                        }
                ))
                .build();

        CoreLogger.get().info(LOG, "DownloadManager initialized: threads=" + this.maxThreads
                + " httpConcurrency=" + httpConcurrency);
    }

    public String createSession() {
        String id = "session-" + System.currentTimeMillis() + "-" + sessionCounter.incrementAndGet();
        sessions.put(id, new DownloadSession(id));
        CoreLogger.get().debug(LOG, "Session created: " + id);
        return id;
    }

    public Optional<DownloadSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public String registerSessionIfAbsent(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new DownloadSession(id)).getSessionId();
    }

    public Collection<DownloadSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public boolean pauseSession(String sessionId) {
        return getSession(sessionId).map(s -> {
            boolean ok = s.pause();
            if (ok) {
                broadcaster.emit("session_paused", Map.of("sessionId", sessionId));
                CoreLogger.get().info(LOG, "Session paused: " + sessionId);
            }
            return ok;
        }).orElse(false);
    }

    public boolean resumeSession(String sessionId) {
        return getSession(sessionId).map(s -> {
            boolean ok = s.resume();
            if (ok) {
                broadcaster.emit("session_resumed", Map.of("sessionId", sessionId));
                CoreLogger.get().info(LOG, "Session resumed: " + sessionId);
            }
            return ok;
        }).orElse(false);
    }

    public boolean cancelSession(String sessionId) {
        return getSession(sessionId).map(s -> {
            boolean ok = s.cancel();
            if (ok) {
                broadcaster.emit("session_cancelled", Map.of("sessionId", sessionId));
                CoreLogger.get().info(LOG, "Session cancelled: " + sessionId);
            }
            return ok;
        }).orElse(false);
    }

    public CompletableFuture<List<DownloadResult>> submitAll(String sessionId, List<DownloadTask> tasks) {
        DownloadSession session = sessions.get(sessionId);
        if (session == null)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown session: " + sessionId));

        for (DownloadTask task : tasks) session.registerTask(task);
        session.markRunning();

        CoreLogger.get().info(LOG, "Session [" + sessionId + "] submitting " + tasks.size() + " tasks");

        broadcaster.emit("session_started", Map.of(
                "sessionId",  sessionId,
                "totalFiles", tasks.size(),
                "totalBytes", tasks.stream().mapToLong(t -> t.expectedSize).sum()
        ));

        FileDownloader fileDownloader = new FileDownloader(http, broadcaster, session, httpSemaphore, priority);

        List<CompletableFuture<DownloadResult>> futures = new ArrayList<>();
        for (DownloadTask task : tasks) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> executeTask(session, task, fileDownloader), pool));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<DownloadResult> results = futures.stream()
                            .map(CompletableFuture::join).toList();

                    if (session.isCancelled()) {
                        CoreLogger.get().info(LOG, "Session finished (cancelled): " + sessionId);
                        return results;
                    }

                    long failedCount = results.stream().filter(DownloadResult::isFailed).count();
                    if (failedCount > 0) {
                        session.markFailed(failedCount + " file(s) failed");
                        broadcaster.emitSessionFailed(sessionId,
                                failedCount + " of " + tasks.size() + " files failed");
                        CoreLogger.get().error(LOG, "Session failed [" + sessionId + "]: " + failedCount + " files");
                    } else {
                        session.markCompleted();
                        Map<String, Object> snap = session.toSnapshot();
                        broadcaster.emitSessionCompleted(sessionId, tasks.size(),
                                (long) snap.get("downloadedBytes"));
                    }
                    return results;
                });
    }

    private DownloadResult executeTask(DownloadSession session, DownloadTask task, FileDownloader fd) {
        DownloadResult result = fd.download(task);
        session.applyResult(result);
        Map<String, Object> snap = session.toSnapshot();
        broadcaster.emitSessionProgress(
                task.sessionId,
                (int) snap.get("completedFiles"),
                (int) snap.get("skippedFiles"),
                (int) snap.get("totalFiles"),
                (int) snap.get("overallPercent"),
                (long) snap.get("downloadedBytes"),
                (long) snap.get("totalBytes")
        );
        return result;
    }

    public List<Map<String, Object>> getRecoverySnapshots() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DownloadSession s : sessions.values()) {
            DownloadSession.Status status = s.getStatus();
            if (status != DownloadSession.Status.COMPLETED) {
                list.add(s.getLastPersistedSnapshot());
            }
        }
        return list;
    }

    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            CoreLogger.get().error(LOG, "DownloadManager pool shutdown interrupted", e);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        CoreLogger.get().info(LOG, "DownloadManager shut down");
    }
}
