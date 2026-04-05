package dev.novastep.core.minecraft.models;

import java.util.List;
import java.util.Map;

public class VersionInfo {

    public String id;
    public String type;
    public String mainClass;
    public String assets;
    public String inheritsFrom;
    public Arguments arguments;
    public String minecraftArguments;
    public Downloads downloads;
    public List<Library> libraries;
    public AssetIndex assetIndex;
    public JavaVersion javaVersion;
    public LoggingConfig logging;
    public int minimumLauncherVersion;

    public static class Downloads {
        public Artifact client;
        public Artifact server;
        public Artifact client_mappings;
        public Artifact server_mappings;
    }

    public static class Artifact {
        public String sha1;
        public long   size;
        public String url;
        public String path;
    }

    public static class Library {
        public String name;
        public LibDownloads downloads;
        public Map<String, String> natives;
        public List<Rule> rules;

        public boolean isAllowed() {
            if (rules == null || rules.isEmpty()) return true;
            boolean result = false;
            for (Rule rule : rules) {
                boolean ruleMatches;
                if (rule.os == null) {
                    ruleMatches = true;
                } else {
                    String osName = rule.os.get("name");
                    ruleMatches = osName != null && osName.equals(currentOsName());
                }
                if (ruleMatches) {
                    result = "allow".equals(rule.action);
                }
            }
            return result;
        }

        public String getNativeClassifier() {
            if (natives == null) return null;
            return natives.get(currentOsName());
        }

        private static String currentOsName() {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win"))  return "windows";
            if (os.contains("mac"))  return "osx";
            return "linux";
        }

        public static class LibDownloads {
            public Artifact artifact;
            public Map<String, Artifact> classifiers;
        }

        public static class Rule {
            public String action;
            public Map<String, String> os;
        }
    }

    public static class AssetIndex {
        public String id;
        public String sha1;
        public long size;
        public long totalSize;
        public String url;
    }

    public static class JavaVersion {
        public String component;
        public int majorVersion;
    }

    public static class LoggingConfig {
        public ClientLogging client;
        public static class ClientLogging {
            public String argument;
            public Artifact file;
            public String type;
        }
    }

    public static class Arguments {
        public List<Object> game;
        public List<Object> jvm;
    }
}
