package dev.novastep.core.server;

import java.nio.file.Path;

import dev.novastep.core.util.SystemResources;

public final class ModLoaderRequest {

    public String  loader;
    public String  loaderVersion;
    public String  minecraftVersion;
    public String  instancePath;
    public String  sharedPath;
    public Integer maxThreads;
    public Boolean debug;

    public String resolvedInstancePath() {
        if (instancePath == null || instancePath.isBlank())
            return Path.of(System.getProperty("user.dir"))
                    .resolve("instances").resolve("default").toAbsolutePath().toString();
        return Path.of(instancePath).toAbsolutePath().toString();
    }

    public Path resolvedLibrariesPath() {
        if (sharedPath != null && !sharedPath.isBlank())
            return Path.of(sharedPath).toAbsolutePath().resolve("libraries");
        return Path.of(resolvedInstancePath()).resolve("libraries");
    }

    public Path resolvedMinecraftJar() {
        return Path.of(resolvedInstancePath())
                .resolve("versions")
                .resolve(minecraftVersion)
                .resolve(minecraftVersion + ".jar");
    }

    public int resolvedMaxThreads() {
        return SystemResources.safeThreads(maxThreads != null ? maxThreads : 0);
    }

    public String validate() {
        if (loader == null || loader.isBlank())
            return "Field 'loader' is required";
        if (minecraftVersion == null || minecraftVersion.isBlank())
            return "Field 'minecraftVersion' is required";
        if (instancePath == null || instancePath.isBlank())
            return "Field 'instancePath' is required";
        return null;
    }
}
