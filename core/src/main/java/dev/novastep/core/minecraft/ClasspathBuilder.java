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

    public ClasspathBuilder(VersionInfo versionInfo, Path instancePath) {
        this.versionInfo = versionInfo;
        this.instancePath = instancePath;
        this.librariesPath = instancePath.resolve("libraries");
        this.assetsPath = instancePath.resolve("assets");
    }

    public ClasspathBuilder(VersionInfo versionInfo, Path instancePath, Path librariesPath, Path assetsPath) {
        this.versionInfo = versionInfo;
        this.instancePath = instancePath;
        this.librariesPath = librariesPath;
        this.assetsPath = assetsPath;
    }

    public static ClasspathBuilder fromRequest(LaunchRequest req, VersionInfo versionInfo) {
        Path inst = Path.of(req.resolvedInstancePath()).toAbsolutePath();
        Path libs = req.resolvedLibrariesPath().toAbsolutePath();
        Path ast = req.resolvedAssetsPath().toAbsolutePath();
        return new ClasspathBuilder(versionInfo, inst, libs, ast);
    }

    public List<Path> buildClasspathEntries() {
        List<Path> entries = new ArrayList<>();
        if (versionInfo.libraries != null) {
            for (VersionInfo.Library lib : versionInfo.libraries) {
                if (!lib.isAllowed()) continue;
                if (lib.downloads == null || lib.downloads.artifact == null) continue;
                if (TaskBuilder.isNativeLib(lib)) continue;
                VersionInfo.Artifact artifact = lib.downloads.artifact;
                if (artifact.path == null || !TaskBuilder.isValidArtifact(artifact)) continue;
                Path jar = librariesPath.resolve(artifact.path);
                if (Files.exists(jar)) entries.add(jar);
            }
        }
        Path clientJar = instancePath.resolve("versions").resolve(versionInfo.id).resolve(versionInfo.id + ".jar");
        if (Files.exists(clientJar)) entries.add(clientJar);
        return entries;
    }

    public String buildClasspathString() {
        String sep = File.pathSeparator;
        StringBuilder sb = new StringBuilder();
        for (Path p : buildClasspathEntries()) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.toAbsolutePath());
        }
        return sb.toString();
    }

    public String getAssetsDir() { return assetsPath.toAbsolutePath().toString(); }

    public Path extractNatives() throws IOException {
        Path nativesDir = instancePath.resolve("versions").resolve(versionInfo.id).resolve("natives");
        Files.createDirectories(nativesDir);
        if (versionInfo.libraries == null) return nativesDir;

        String os = TaskBuilder.currentOs(), arch = TaskBuilder.currentArch();
        int extracted = 0;

        for (VersionInfo.Library lib : versionInfo.libraries) {
            if (!lib.isAllowed() || !TaskBuilder.isNativeLib(lib)) continue;
            for (Path nativeJar : findNativeJarPaths(lib, os, arch)) {
                if (!Files.exists(nativeJar)) {
                    System.err.println("[Natives] JAR not found: " + nativeJar);
                    continue;
                }
                int count = extractFromJar(nativeJar, nativesDir);
                if (count > 0)
                    System.out.println("[Natives] Extracted " + count + " files from: " + nativeJar.getFileName());
                extracted += count;
            }
        }

        long total = countNativeFiles(nativesDir);
        System.out.println("[Natives] " + extracted + " extracted, " + total + " total in " + nativesDir);
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
                if (a != null && a.path != null && TaskBuilder.isValidArtifact(a)) {
                    result.add(librariesPath.resolve(a.path));
                    return result;
                }
            }
            for (var e : lib.downloads.classifiers.entrySet()) {
                if (e.getKey().startsWith("natives-" + os) && e.getValue().path != null
                        && TaskBuilder.isValidArtifact(e.getValue())) {
                    result.add(librariesPath.resolve(e.getValue().path));
                    return result;
                }
            }
        }

        if (lib.downloads.classifiers != null) {
            for (var e : lib.downloads.classifiers.entrySet()) {
                if (e.getKey().startsWith("natives-" + os) && e.getValue().path != null
                        && TaskBuilder.isValidArtifact(e.getValue()))
                    result.add(librariesPath.resolve(e.getValue().path));
            }
            if (!result.isEmpty()) return result;
        }

        if (lib.downloads.artifact != null && lib.downloads.artifact.path != null
                && TaskBuilder.isValidArtifact(lib.downloads.artifact))
            result.add(librariesPath.resolve(lib.downloads.artifact.path));

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
                String fileName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
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