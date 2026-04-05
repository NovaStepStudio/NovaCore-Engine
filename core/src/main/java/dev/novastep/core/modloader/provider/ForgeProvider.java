package dev.novastep.core.modloader.provider;

import dev.novastep.core.modloader.model.LoaderVersion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ForgeProvider extends AbstractForgeProvider {

    private static final String MAVEN_META = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String MAVEN_BASE = "https://maven.minecraftforge.net/";

    @Override
    public String name() {
        return "forge";
    }

    @Override
    protected String installerUrl(String versionId) {
        return MAVEN_BASE + "net/minecraftforge/forge/" + versionId + "/forge-" + versionId + "-installer.jar";
    }

    @Override
    protected String mavenRepoBase() {
        return MAVEN_BASE;
    }

    @Override
    protected List<String> listAllVersions() throws IOException, InterruptedException {
        return parseMavenMetadataVersions(get(MAVEN_META));
    }

    @Override
    protected List<String> filterForMinecraft(List<String> all, String mcVersion) {
        String prefix = mcVersion + "-";
        List<String> result = new ArrayList<>();
        for (String v : all) {
            if (v.startsWith(prefix)) result.add(v);
        }
        return result;
    }

    @Override
    protected String versionIdForInstaller(String mcVersion, String loaderVersion) {
        if (loaderVersion.startsWith(mcVersion + "-")) return loaderVersion;
        return mcVersion + "-" + loaderVersion;
    }

    @Override
    public List<LoaderVersion> getVersions(String mcVersion) throws IOException, InterruptedException {
        List<String> all      = listAllVersions();
        List<String> filtered = filterForMinecraft(all, mcVersion);
        List<LoaderVersion> result = new ArrayList<>();
        for (String v : filtered) {
            String loaderOnly = v.startsWith(mcVersion + "-")
                    ? v.substring(mcVersion.length() + 1) : v;
            result.add(new LoaderVersion(loaderOnly, mcVersion, true));
        }
        return result;
    }
}
