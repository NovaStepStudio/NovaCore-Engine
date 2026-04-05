package dev.novastep.core.modloader.model;

public final class InstalledLoader {

    public String loaderType;
    public String loaderVersion;
    public String minecraftVersion;
    public String versionJsonId;
    public String installerJarPath;
    public long   installedAt;

    public InstalledLoader() {}

    public InstalledLoader(
            String loaderType,
            String loaderVersion,
            String minecraftVersion,
            String versionJsonId,
            String installerJarPath) {
        this.loaderType       = loaderType;
        this.loaderVersion    = loaderVersion;
        this.minecraftVersion = minecraftVersion;
        this.versionJsonId    = versionJsonId;
        this.installerJarPath = installerJarPath;
        this.installedAt      = System.currentTimeMillis();
    }
}
