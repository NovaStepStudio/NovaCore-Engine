package dev.novastep.core.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public final class SystemResources {

    public static final int MIN_DOWNLOAD_THREADS = 4;
    public static final int MAX_DOWNLOAD_THREADS = 32;
    public static final int MIN_MC_RAM_MB = 512;
    public static final int MAX_MC_RAM_MB = 8192;
    private static final double DOWNLOAD_CORE_FRACTION = 0.40;
    private static final double MC_RAM_FRACTION = 0.55;
    private static final long OS_RESERVED_RAM_MB = 512;
    private SystemResources() {}

    public static int availableCores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static int optimalDownloadThreads() {
        int cores   = availableCores();
        int threads = (int) Math.ceil(cores * DOWNLOAD_CORE_FRACTION);
        return Math.max(MIN_DOWNLOAD_THREADS, Math.min(MAX_DOWNLOAD_THREADS, threads));
    }

    public static int safeThreads(int requested) {
        int optimal = optimalDownloadThreads();
        if (requested <= 0) return optimal;
        return Math.min(requested, Math.min(MAX_DOWNLOAD_THREADS, availableCores()));
    }

    public static long totalSystemRamMb() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                return sunOs.getTotalMemorySize() / (1024 * 1024);
            }
        } catch (Exception ignored) {}
        
        return 4096;
    }

    public static long estimatedFreeRamMb() {
        try {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                long freeBytes = sunOs.getFreeMemorySize();
                return Math.max(0, (freeBytes / (1024 * 1024)) - OS_RESERVED_RAM_MB);
            }
        } catch (Exception ignored) {}
        
        return 2048;
    }

    public static int recommendedMinRamMb() {
        return MIN_MC_RAM_MB;
    }

    public static int recommendedMaxRamMb() {
        long freeRam = estimatedFreeRamMb();
        int  recommended = (int)(freeRam * MC_RAM_FRACTION);
        
        recommended = (recommended / 256) * 256;
        return Math.max(MIN_MC_RAM_MB, Math.min(MAX_MC_RAM_MB, recommended));
    }

    public static int[] safeRam(int requestedMin, int requestedMax) {
        int maxAllowed = recommendedMaxRamMb();
        int min = requestedMin <= 0 ? recommendedMinRamMb() : requestedMin;
        int max = requestedMax <= 0 ? maxAllowed : Math.min(requestedMax, maxAllowed);
        
        if (min > max) min = Math.min(min, max);
        return new int[]{ min, max };
    }

    public static java.util.List<String> gcFlags(String gcPreset, int maxRamMb) {
        java.util.List<String> flags = new java.util.ArrayList<>();

        String preset = gcPreset != null ? gcPreset.toLowerCase() : "auto";

        if ("auto".equals(preset) || preset.isEmpty()) {
            preset = maxRamMb >= 6144 ? "zgc"
                   : maxRamMb >= 3072 ? "g1gc_optimized"
                   : "g1gc_basic";
        }

        switch (preset) {
            case "zgc" -> {
                
                flags.add("-XX:+UseZGC");
                flags.add("-XX:+ZGenerational");  
                flags.add("-XX:ZCollectionInterval=0");
            }
            case "shenandoah" -> {
                
                flags.add("-XX:+UseShenandoahGC");
                flags.add("-XX:ShenandoahGCMode=iu");
            }
            case "g1gc_optimized" -> {
                
                flags.add("-XX:+UseG1GC");
                flags.add("-XX:+UnlockExperimentalVMOptions");
                flags.add("-XX:G1NewSizePercent=20");
                flags.add("-XX:G1ReservePercent=20");
                flags.add("-XX:MaxGCPauseMillis=50");
                flags.add("-XX:G1HeapRegionSize=32M");
                flags.add("-XX:+ParallelRefProcEnabled");
                flags.add("-XX:+UseLargePages");
                flags.add("-XX:+DisableExplicitGC");
            }
            default -> {  
                flags.add("-XX:+UseG1GC");
                flags.add("-XX:+UnlockExperimentalVMOptions");
                flags.add("-XX:G1NewSizePercent=20");
                flags.add("-XX:G1ReservePercent=20");
                flags.add("-XX:MaxGCPauseMillis=50");
            }
        }
        return flags;
    }

    public static java.util.Map<String, Object> snapshot() {
        int  cores = availableCores();
        long totalRam = totalSystemRamMb();
        long freeRam = estimatedFreeRamMb();
        int  dlThreads = optimalDownloadThreads();
        int  mcMinRam = recommendedMinRamMb();
        int  mcMaxRam = recommendedMaxRamMb();

        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("cpu", java.util.Map.of(
            "cores", cores,
            "optimalDlThreads", dlThreads
        ));
        m.put("ram", java.util.Map.of(
            "totalMb", totalRam,
            "estimatedFreeMb", freeRam,
            "reservedForOsMb", OS_RESERVED_RAM_MB
        ));
        m.put("recommended", java.util.Map.of(
            "downloadThreads", dlThreads,
            "mcMinRamMb", mcMinRam,
            "mcMaxRamMb", mcMaxRam,
            "gcPreset", mcMaxRam >= 6144 ? "zgc" : mcMaxRam >= 3072 ? "g1gc_optimized" : "g1gc_basic"
        ));
        return m;
    }
}