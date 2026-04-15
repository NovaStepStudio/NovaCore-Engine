package dev.novastep.core.util;

import dev.novastep.core.log.CoreLogger;

public final class ProcessUtils {

    private static final String LOG = "ProcessUtils";

    private ProcessUtils() {
    }

    public static void killTree(long pid) {
        if (pid <= 0)
            return;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid));
            } else {
                pb = new ProcessBuilder("pkill", "-9", "-P", String.valueOf(pid));
            }
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            CoreLogger.get().warn(LOG, "Error al terminar proceso " + pid + ": " + e.getMessage());
        }
    }

    public static void addShutdownHook(Runnable cleanup) {
        Runtime.getRuntime().addShutdownHook(new Thread(cleanup, "NovaCore-ShutdownHook"));
    }
}
