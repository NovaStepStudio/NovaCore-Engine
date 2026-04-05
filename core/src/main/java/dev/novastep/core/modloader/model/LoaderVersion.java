package dev.novastep.core.modloader.model;

public final class LoaderVersion {

    public final String loaderVersion;
    public final String minecraftVersion;
    public final boolean stable;

    public LoaderVersion(String loaderVersion, String minecraftVersion, boolean stable) {
        this.loaderVersion    = loaderVersion;
        this.minecraftVersion = minecraftVersion;
        this.stable           = stable;
    }

    @Override
    public String toString() {
        return "LoaderVersion{loader='" + loaderVersion + "', mc='" + minecraftVersion + "', stable=" + stable + "}";
    }
}
