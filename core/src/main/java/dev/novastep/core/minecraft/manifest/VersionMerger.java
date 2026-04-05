package dev.novastep.core.minecraft.manifest;

import dev.novastep.core.minecraft.models.VersionInfo;

import java.util.ArrayList;
import java.util.List;

public final class VersionMerger {

    private VersionMerger() {}

    public static VersionInfo merge(VersionInfo parent, VersionInfo child) {
        VersionInfo merged = new VersionInfo();

        merged.id        = child.id;
        merged.type      = child.type      != null ? child.type      : parent.type;
        merged.mainClass = child.mainClass != null ? child.mainClass : parent.mainClass;
        merged.assets    = child.assets    != null ? child.assets    : parent.assets;
        merged.assetIndex     = child.assetIndex     != null ? child.assetIndex     : parent.assetIndex;
        merged.javaVersion    = child.javaVersion    != null ? child.javaVersion    : parent.javaVersion;
        merged.logging        = child.logging        != null ? child.logging        : parent.logging;
        merged.downloads      = child.downloads      != null ? child.downloads      : parent.downloads;
        merged.minimumLauncherVersion = Math.max(
                child.minimumLauncherVersion, parent.minimumLauncherVersion);

        merged.minecraftArguments = child.minecraftArguments != null
                ? child.minecraftArguments
                : parent.minecraftArguments;

        merged.libraries  = mergeLibraries(parent.libraries, child.libraries);
        merged.arguments  = mergeArguments(parent.arguments, child.arguments);

        return merged;
    }

    private static List<VersionInfo.Library> mergeLibraries(
            List<VersionInfo.Library> parent,
            List<VersionInfo.Library> child) {
        List<VersionInfo.Library> result = new ArrayList<>();
        if (parent != null) result.addAll(parent);
        if (child  != null) result.addAll(child);
        return result;
    }

    private static VersionInfo.Arguments mergeArguments(
            VersionInfo.Arguments parent,
            VersionInfo.Arguments child) {
        if (parent == null && child == null) return null;
        VersionInfo.Arguments merged = new VersionInfo.Arguments();
        merged.game = mergeObjectLists(
                parent != null ? parent.game : null,
                child  != null ? child.game  : null);
        merged.jvm  = mergeObjectLists(
                parent != null ? parent.jvm  : null,
                child  != null ? child.jvm   : null);
        return merged;
    }

    private static List<Object> mergeObjectLists(List<Object> parent, List<Object> child) {
        List<Object> result = new ArrayList<>();
        if (parent != null) result.addAll(parent);
        if (child  != null) result.addAll(child);
        return result;
    }
}
