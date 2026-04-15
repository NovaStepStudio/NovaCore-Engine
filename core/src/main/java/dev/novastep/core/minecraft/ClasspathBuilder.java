package dev.novastep.core.minecraft;

import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.server.LaunchRequest;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class ClasspathBuilder {

    private final VersionInfo versionInfo;
    private final Path instancePath;
    private final Path librariesPath;
    private final Path assetsPath;
    private final String vanillaVersionId;
    private Path resolvedNativesDir;
    
    private final List<Path> modloaderEntries = new ArrayList<>();

    public ClasspathBuilder(VersionInfo versionInfo, Path instancePath,
                             Path librariesPath, Path assetsPath, String vanillaVersionId) {
        this.versionInfo      = versionInfo;
        this.instancePath     = instancePath;
        this.librariesPath    = librariesPath;
        this.assetsPath       = assetsPath;
        this.vanillaVersionId = vanillaVersionId != null ? vanillaVersionId : versionInfo.id;
    }

    public ClasspathBuilder(VersionInfo versionInfo, Path instancePath) {
        this(versionInfo, instancePath,
                instancePath.resolve("libraries"),
                instancePath.resolve("assets"),
                versionInfo.id);
    }

    public ClasspathBuilder(VersionInfo versionInfo, Path instancePath,
                             Path librariesPath, Path assetsPath) {
        this(versionInfo, instancePath, librariesPath, assetsPath, versionInfo.id);
    }

    public static ClasspathBuilder fromRequest(LaunchRequest req, VersionInfo versionInfo) {
        Path inst = Path.of(req.resolvedInstancePath()).toAbsolutePath();
        Path libs = req.resolvedLibrariesPath().toAbsolutePath();
        Path ast  = req.resolvedAssetsPath().toAbsolutePath();
        return new ClasspathBuilder(versionInfo, inst, libs, ast, versionInfo.id);
    }

    public static ClasspathBuilder fromRequest(LaunchRequest req, VersionInfo versionInfo,
                                                String vanillaVersionId) {
        Path inst = Path.of(req.resolvedInstancePath()).toAbsolutePath();
        Path libs = req.resolvedLibrariesPath().toAbsolutePath();
        Path ast  = req.resolvedAssetsPath().toAbsolutePath();
        return new ClasspathBuilder(versionInfo, inst, libs, ast, vanillaVersionId);
    }

    public void appendModloaderEntries(List<Path> entries) {
        for (Path p : entries) {
            if (!modloaderEntries.contains(p)) modloaderEntries.add(p);
        }
    }

    public String getVanillaVersionId() {
        return vanillaVersionId;
    }

    public String getLibrariesPath() {
        return librariesPath.toAbsolutePath().toString();
    }

    public List<Path> buildClasspathEntries() {
        LinkedHashSet<Path> seen = new LinkedHashSet<>();

        if (versionInfo.libraries != null) {
            for (VersionInfo.Library lib : versionInfo.libraries) {
                if (!lib.isAllowed()) continue;
                if (TaskBuilder.isNativeLib(lib)) continue;

                Path jar = null;
                if (lib.downloads != null && lib.downloads.artifact != null && lib.downloads.artifact.path != null) {
                    jar = librariesPath.resolve(lib.downloads.artifact.path);
                } else if (lib.name != null && !lib.name.isEmpty()) {
                    jar = resolveFromMavenCoordinates(lib.name);
                }

                if (jar != null && Files.exists(jar)) {
                    seen.add(jar);
                }
            }
        }

        for (Path extra : modloaderEntries) {
            if (Files.exists(extra)) seen.add(extra);
        }


        Path clientJar = instancePath.resolve("versions")
                .resolve(vanillaVersionId).resolve(vanillaVersionId + ".jar");
        if (Files.exists(clientJar)) seen.add(clientJar);

        return new ArrayList<>(seen);
    }

    public String buildClasspathString() {
        StringBuilder sb = new StringBuilder();
        for (Path p : buildClasspathEntries()) {
            if (!sb.isEmpty()) sb.append(File.pathSeparator);
            sb.append(p.toAbsolutePath());
        }
        return sb.toString();
    }

    public String getAssetsDir() {
        return assetsPath.toAbsolutePath().toString();
    }


    private Path resolveNativesDirectoryFromArguments() {
        Path defaultNatives = instancePath.resolve("versions")
                .resolve(vanillaVersionId).resolve("natives");

        List<String> jvmArgs = extractJvmArguments(versionInfo);
        for (String arg : jvmArgs) {
            if (arg.startsWith("-Djava.library.path=")) {
                String value = arg.substring("-Djava.library.path=".length());
                String resolved = value.replace("${natives_directory}", defaultNatives.toString());
                Path libPath = Path.of(resolved);
                return libPath.toAbsolutePath().normalize();
            }
        }
        return defaultNatives;
    }

    private List<String> extractJvmArguments(VersionInfo info) {
        List<String> result = new ArrayList<>();
        if (info.arguments == null || info.arguments.jvm == null) {
            return result;
        }
        String currentOs = TaskBuilder.currentOs();
        for (Object entry : info.arguments.jvm) {
            if (entry instanceof String) {
                result.add((String) entry);
            } else if (entry instanceof Map) {
                // Rules-based argument
                Map<?, ?> ruleObj = (Map<?, ?>) entry;
                Object rules = ruleObj.get("rules");
                Object valueObj = ruleObj.get("value");
                if (rules == null || valueObj == null) continue;
                boolean allowed = false;
                if (rules instanceof List) {
                    for (Object r : (List<?>) rules) {
                        Map<?, ?> ruleMap = (Map<?, ?>) r;
                        String action = (String) ruleMap.get("action");
                        Map<?, ?> os = (Map<?, ?>) ruleMap.get("os");
                        boolean matches = true;
                        if (os != null) {
                            String osName = (String) os.get("name");
                            if (osName != null && !osName.equals(currentOs)) {
                                matches = false;
                            }
                        }
                        if (matches) {
                            allowed = "allow".equals(action);
                        }
                    }
                }
                if (allowed) {
                    if (valueObj instanceof String) {
                        result.add((String) valueObj);
                    } else if (valueObj instanceof List) {
                        for (Object v : (List<?>) valueObj) {
                            if (v instanceof String) result.add((String) v);
                        }
                    }
                }
            }
        }
        return result;
    }

    public String getNativesDir() {
        if (resolvedNativesDir == null) {
            resolvedNativesDir = resolveNativesDirectoryFromArguments();
        }
        return resolvedNativesDir.toString();
    }
    public Path extractNatives() throws IOException {
        Path nativesDir = Path.of(getNativesDir());
        Files.createDirectories(nativesDir);
        if (versionInfo.libraries == null) return nativesDir;

        String os = TaskBuilder.currentOs(), arch = TaskBuilder.currentArch();
        int extracted = 0;

        for (VersionInfo.Library lib : versionInfo.libraries) {
            if (!lib.isAllowed() || !TaskBuilder.isNativeLib(lib)) continue;
            for (Path nativeJar : findNativeJarPaths(lib, os, arch)) {
                if (!Files.exists(nativeJar)) continue;
                extracted += extractFromJar(nativeJar, nativesDir);
            }
        }

        long total = countNativeFiles(nativesDir);
        if (total == 0 && versionInfo.inheritsFrom != null && !versionInfo.inheritsFrom.isBlank()) {
            Path parentNativesDir = instancePath.resolve("versions")
                    .resolve(versionInfo.inheritsFrom).resolve("natives");
            if (Files.exists(parentNativesDir) && countNativeFiles(parentNativesDir) > 0) {
                System.out.println("[Natives] Fallback a natives de versión padre: " + versionInfo.inheritsFrom);
                return parentNativesDir;
            }
        }

        System.out.println("[Natives] " + extracted + " extracted, " + total + " total.");
        return nativesDir;
    }

    private List<Path> findNativeJarPaths(VersionInfo.Library lib, String os, String arch) {
        if (lib.downloads == null) return List.of();
        List<Path> result = new ArrayList<>();

        if (lib.natives != null && lib.natives.containsKey(os) && lib.downloads.classifiers != null) {
            String archNum = arch.equals("x86") ? "32" : "64";
            String[] candidates = {
                lib.natives.get(os).replace("${arch}", archNum),
                "natives-" + os
            };
            for (String key : candidates) {
                VersionInfo.Artifact a = lib.downloads.classifiers.get(key);
                if (a != null && a.path != null) {
                    Path jar = librariesPath.resolve(a.path);
                    if (Files.exists(jar)) { result.add(jar); return result; }
                }
            }
            for (var e : lib.downloads.classifiers.entrySet()) {
                if (e.getKey().startsWith("natives-" + os) && e.getValue().path != null) {
                    Path jar = librariesPath.resolve(e.getValue().path);
                    if (Files.exists(jar)) { result.add(jar); return result; }
                }
            }
        }

        if (lib.downloads.classifiers != null) {
            for (var e : lib.downloads.classifiers.entrySet()) {
                if (e.getKey().startsWith("natives-" + os) && e.getValue().path != null) {
                    Path jar = librariesPath.resolve(e.getValue().path);
                    if (Files.exists(jar)) result.add(jar);
                }
            }
            if (!result.isEmpty()) return result;
        }

        if (lib.downloads.artifact != null && lib.downloads.artifact.path != null) {
            Path jar = librariesPath.resolve(lib.downloads.artifact.path);
            if (Files.exists(jar)) result.add(jar);
        }

        return result;
    }

    private int extractFromJar(Path jarPath, Path destDir) {
        int count = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF")) continue;
                if (!isNativeFile(name)) continue;
                String fileName = name.contains("/")
                        ? name.substring(name.lastIndexOf('/') + 1) : name;
                Path dest = destDir.resolve(fileName);
                if (Files.exists(dest)) continue;
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, dest);
                    dest.toFile().setExecutable(true);
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.println("[Natives] Extract failed from " + jarPath.getFileName() + ": " + e.getMessage());
        }
        return count;
    }

    private Path resolveFromMavenCoordinates(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        String groupId = parts[0].replace('.', '/');
        String artifactId = parts[1];
        String version = parts[2];
        
        String classifier = parts.length > 3 ? parts[3] : null;
        String jarName = artifactId + "-" + version + (classifier != null ? "-" + classifier : "") + ".jar";
        return librariesPath.resolve(groupId).resolve(artifactId).resolve(version).resolve(jarName);
    }

    private static boolean isNativeFile(String n) {
        String l = n.toLowerCase();
        return l.endsWith(".dll") || l.endsWith(".so") || l.endsWith(".dylib") || l.endsWith(".jnilib");
    }

    private long countNativeFiles(Path dir) {
        try (var s = Files.list(dir)) {
            return s.filter(p -> isNativeFile(p.getFileName().toString())).count();
        } catch (IOException e) { return 0; }
    }
}