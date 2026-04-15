package dev.novastep.core.modloader.provider;

import com.google.gson.Gson;
import dev.novastep.core.log.CoreLogger;
import dev.novastep.core.minecraft.models.VersionInfo;
import dev.novastep.core.modloader.ModLoaderProvider;
import dev.novastep.core.modloader.model.DownloadPlan;
import dev.novastep.core.modloader.model.ExecutionPlan;
import dev.novastep.core.modloader.model.InstalledLoader;
import dev.novastep.core.modloader.model.LoaderVersion;
import dev.novastep.core.util.JavaResolver;
import dev.novastep.core.websocket.EventBroadcaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class OptiFineProvider implements ModLoaderProvider {

    private static final String LOG           = "OptiFineProvider";
    private static final String DOWNLOADS_URL = "https://optifine.net/downloads";
    private static final String ADDOWNLOAD    = "https://optifine.net/addownload?f=";
    private static final String CFR_MAVEN_URL = "https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar";

    private static final Pattern FILE_PATTERN = Pattern.compile("OptiFine_([\\d.]+)_HD_U_([A-Z]\\d+(?:_pre\\d+)?)\\.jar");
    private static final Pattern HREF_PATTERN = Pattern.compile("href=['\"]([^'\"]*OptiFine_[^'\"]+\\.jar[^'\"]*)['\"\\s]");
    private static final Pattern GETWD_PATTERN = Pattern.compile("File\\s+(\\w+)\\s*=\\s*Utils\\.getWorkingDirectory\\s*\\(\\s*\\)\\s*;");

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @Override
    public String name() {
        return "optifine";
    }

    @Override
    public List<LoaderVersion> getVersions(String mcVersion) throws IOException, InterruptedException {
        String html = get(DOWNLOADS_URL);
        return parseVersions(html, mcVersion);
    }

    private List<LoaderVersion> parseVersions(String html, String mcVersion) {
        List<LoaderVersion> result = new ArrayList<>();
        Matcher href = HREF_PATTERN.matcher(html);
        while (href.find()) {
            Matcher fm = FILE_PATTERN.matcher(href.group(1));
            if (!fm.find()) continue;
            if (!fm.group(1).equals(mcVersion)) continue;
            String build = fm.group(2);
            result.add(new LoaderVersion("HD_U_" + build, mcVersion, !build.contains("_pre")));
        }
        return result;
    }

    @Override
    public DownloadPlan resolveDownload(
            String mcVersion, String loaderVersion,
            Path instancePath, Path librariesPath) throws IOException, InterruptedException {

        String buildCode = loaderVersion.replace("HD_U_", "");
        String fileName  = "OptiFine_" + mcVersion + "_HD_U_" + buildCode + ".jar";
        Path   dest      = instancePath.resolve("installers").resolve(fileName);

        Files.createDirectories(dest.getParent());

        List<DownloadPlan.Entry> entries = new ArrayList<>();
        entries.add(DownloadPlan.Entry.installer(fileName, ADDOWNLOAD + fileName, dest));
        return DownloadPlan.withInstaller(entries, dest);
    }

    @Override
    public boolean requiresInstallerRun() {
        return true;
    }

    @Override
    public void runInstaller(
            String sessionId,
            InstalledLoader loader,
            Path instancePath,
            Path librariesPath,
            Path minecraftJar,
            EventBroadcaster broadcaster) throws Exception {

        Path   installerJar = Path.of(loader.installerJarPath);
        String javaExec     = JavaResolver.resolve(instancePath);

        CoreLogger.get().info(LOG, "[" + sessionId + "] Running OptiFine installer: "
                + installerJar.getFileName());
        broadcaster.emit("modloader_install_start", Map.of(
                "sessionId", sessionId,
                "loader",    "optifine",
                "version",   loader.loaderVersion));

        Path cfrJar    = getCfrJar(instancePath);
        Path patchedJar = patchOptiFineInstaller(installerJar, cfrJar, javaExec);

        Path tempDir = Files.createTempDirectory("novacore-optifine-");
        try {
            createLauncherProfiles(tempDir);

            if (Files.exists(minecraftJar)) {
                Path versionDir = tempDir.resolve("versions").resolve(loader.minecraftVersion);
                Files.createDirectories(versionDir);
                Files.copy(minecraftJar,
                        versionDir.resolve(loader.minecraftVersion + ".jar"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            CoreLogger.get().info(LOG, "[" + sessionId + "] Running patched installer with --mcdir");

            Process process = new ProcessBuilder(
                    javaExec, "-Djava.awt.headless=true",
                    "-jar", patchedJar.toAbsolutePath().toString(),
                    "--mcdir", tempDir.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    CoreLogger.get().debug(LOG, "[" + sessionId + "][optifine] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("OptiFine installer exited with code " + exitCode);
            }

            integrateResults(sessionId, tempDir, instancePath, librariesPath, loader.minecraftVersion);

        } finally {
            deleteDirectory(tempDir);
            Files.deleteIfExists(patchedJar);
        }

        broadcaster.emit("modloader_install_done", Map.of(
                "sessionId", sessionId,
                "loader",    "optifine",
                "versionId", loader.versionJsonId));

        CoreLogger.get().info(LOG, "[" + sessionId + "] OptiFine installed successfully.");
    }

    private Path patchOptiFineInstaller(Path installerJar, Path cfrJar, String javaExec)
            throws Exception {

        Path workDir = Files.createTempDirectory("novacore-optifine-patch-");
        Path srcDir  = workDir.resolve("src");
        Path binDir  = workDir.resolve("bin");
        Files.createDirectories(srcDir);
        Files.createDirectories(binDir);

        Path extractedClass = workDir.resolve("optifine").resolve("Installer.class");
        Files.createDirectories(extractedClass.getParent());

        try (JarFile jar = new JarFile(installerJar.toFile())) {
            java.util.jar.JarEntry entry = jar.getJarEntry("optifine/Installer.class");
            if (entry == null) throw new IOException("optifine/Installer.class not found in OptiFine JAR");
            try (InputStream in = jar.getInputStream(entry)) {
                Files.copy(in, extractedClass, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Process cfr = new ProcessBuilder(
                javaExec, "-jar", cfrJar.toAbsolutePath().toString(),
                extractedClass.toAbsolutePath().toString(),
                "--outputdir", srcDir.toAbsolutePath().toString(),
                "--silent", "true")
                .redirectErrorStream(true)
                .start();
        cfr.waitFor();

        Path installerJava = findFile(srcDir, "Installer.java");
        if (installerJava == null) {
            deleteDirectory(workDir);
            throw new IOException("CFR did not generate Installer.java");
        }

        String source = Files.readString(installerJava, StandardCharsets.UTF_8);

        if (!source.contains("package optifine")) {
            source = "package optifine;\n\n" + source;
        }

        Matcher matcher = GETWD_PATTERN.matcher(source);
        if (!matcher.find()) {
            deleteDirectory(workDir);
            throw new IOException("Could not find Utils.getWorkingDirectory() in Installer.java");
        }

        String varName = matcher.group(1);
        String replacement =
                "File " + varName + " = null;\n" +
                "        for (int _i = 0; _i < args.length; _i++) {\n" +
                "            if (args[_i].equals(\"--mcdir\") && _i + 1 < args.length) {\n" +
                "                " + varName + " = new File(args[++_i]);\n" +
                "            }\n" +
                "        }\n" +
                "        if (" + varName + " == null) {\n" +
                "            " + varName + " = Utils.getWorkingDirectory();\n" +
                "        }";

        source = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
        Files.writeString(installerJava, source, StandardCharsets.UTF_8);

        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            deleteDirectory(workDir);
            throw new IOException("javac not available — JDK required for OptiFine patching");
        }

        int result = compiler.run(null, null, null,
                "-source", "8", "-target", "8",
                "-cp", installerJar.toAbsolutePath().toString(),
                "-d", binDir.toAbsolutePath().toString(),
                installerJava.toAbsolutePath().toString());

        if (result != 0) {
            result = compiler.run(null, null, null,
                    "-cp", installerJar.toAbsolutePath().toString(),
                    "-d", binDir.toAbsolutePath().toString(),
                    installerJava.toAbsolutePath().toString());
        }

        if (result != 0) {
            deleteDirectory(workDir);
            throw new IOException("Failed to compile patched Installer.java (javac exit " + result + ")");
        }

        Path compiledClass = findFile(binDir, "Installer.class");
        if (compiledClass == null) {
            deleteDirectory(workDir);
            throw new IOException("Compiled Installer.class not found after compilation");
        }

        Path patchedJar = installerJar.resolveSibling(
                installerJar.getFileName().toString().replace(".jar", "_patched.jar"));

        replaceEntryInJar(installerJar, patchedJar, "optifine/Installer.class", compiledClass);

        deleteDirectory(workDir);
        return patchedJar;
    }

    private void replaceEntryInJar(Path sourceJar, Path outputJar,
                                    String entryName, Path replacement) throws IOException {
        byte[] newBytes = Files.readAllBytes(replacement);

        try (ZipInputStream zin  = new ZipInputStream(Files.newInputStream(sourceJar));
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(outputJar))) {

            ZipEntry entry;
            boolean replaced = false;

            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                zout.putNextEntry(new ZipEntry(name));
                if (name.equals(entryName)) {
                    zout.write(newBytes);
                    replaced = true;
                } else {
                    zin.transferTo(zout);
                }
                zout.closeEntry();
                zin.closeEntry();
            }

            if (!replaced) {
                zout.putNextEntry(new ZipEntry(entryName));
                zout.write(newBytes);
                zout.closeEntry();
            }
        }
    }

    private Path getCfrJar(Path instancePath) throws IOException, InterruptedException {
        Path toolsDir = instancePath.getParent() != null
                ? instancePath.getParent().resolve("tools")
                : instancePath.resolve("tools");
        Files.createDirectories(toolsDir);

        Path cfrJar = toolsDir.resolve("cfr-0.152.jar");
        if (Files.exists(cfrJar)) return cfrJar;

        CoreLogger.get().info(LOG, "Downloading CFR decompiler...");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(CFR_MAVEN_URL))
                .GET()
                .timeout(Duration.ofSeconds(120))
                .build();
        HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) {
            throw new IOException("Could not download CFR: HTTP " + res.statusCode());
        }
        Files.copy(res.body(), cfrJar, StandardCopyOption.REPLACE_EXISTING);
        CoreLogger.get().info(LOG, "CFR downloaded: " + cfrJar);
        return cfrJar;
    }

    private void integrateResults(
            String sessionId,
            Path tempDir,
            Path instancePath,
            Path librariesPath,
            String mcVersion) throws IOException {

        Path tempVersions = tempDir.resolve("versions");
        if (Files.isDirectory(tempVersions)) {
            try (var stream = Files.list(tempVersions)) {
                stream.filter(Files::isDirectory)
                        .filter(p -> !p.getFileName().toString().equals(mcVersion))
                        .forEach(vDir -> {
                            try {
                                Path dest = instancePath.resolve("versions").resolve(vDir.getFileName());
                                copyTree(vDir, dest);
                                CoreLogger.get().info(LOG, "[" + sessionId + "] Integrated version: "
                                        + vDir.getFileName());
                            } catch (IOException e) {
                                CoreLogger.get().warn(LOG, "[" + sessionId + "] Failed copying version "
                                        + vDir.getFileName() + ": " + e.getMessage());
                            }
                        });
            }
        }

        Path tempLibs = tempDir.resolve("libraries");
        if (Files.isDirectory(tempLibs)) {
            copyTree(tempLibs, librariesPath);
            CoreLogger.get().info(LOG, "[" + sessionId + "] Integrated OptiFine libraries.");
        }
    }

    private static void copyTree(Path src, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (var stream = Files.walk(src)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path target = dest.resolve(src.relativize(p));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void createLauncherProfiles(Path dir) throws IOException {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("name", "NovaCore");
        profile.put("type", "custom");
        profile.put("created", "1970-01-01T00:00:00.000Z");
        profile.put("lastUsed", "1970-01-01T00:00:00.000Z");
        profile.put("lastVersionId", "latest-release");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("selectedProfile", "NovaCore");
        root.put("profiles", Map.of("NovaCore", profile));
        root.put("clientToken", UUID.randomUUID().toString().replace("-", ""));
        root.put("authenticationDatabase", Map.of());
        root.put("settings", Map.of("enableSnapshots", false));
        root.put("version", 3);

        Files.writeString(dir.resolve("launcher_profiles.json"), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static Path findFile(Path root, String filename) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> p.getFileName().toString().equals(filename)).findFirst().orElse(null);
        }
    }

    private static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) return;
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
        } catch (IOException ignored) {}
    }

    @Override
    public ExecutionPlan buildExecution(
            InstalledLoader loader, VersionInfo vanillaInfo,
            Path instancePath, Path librariesPath) {
        return null;
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 NovaCore/" + dev.novastep.core.CoreVersion.get())
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " from: " + url);
        }
        return res.body();
    }
}
