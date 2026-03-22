package dev.novastep.core.minecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.novastep.core.minecraft.models.VersionInfo;

public class ArgumentResolver {

    public static class LaunchContext {
        public final String username, version, gameDir, assetsDir, assetIndex;
        public final String uuid, accessToken, userType, clientId, xuid;
        public final String versionType, nativesDir, classpathStr;
        public final String launcherName, launcherVersion;
        public final int    width, height;

        public LaunchContext(
            String username, String version, String gameDir, String assetsDir,
            String assetIndex, String uuid, String accessToken, String userType,
            String clientId, String xuid, String versionType, String nativesDir,
            String classpathStr, String launcherName, String launcherVersion,
            int width, int height
        ) {
            this.username = username; this.version = version;
            this.gameDir = gameDir; this.assetsDir = assetsDir;
            this.assetIndex = assetIndex; this.uuid = uuid;
            this.accessToken = accessToken; this.userType = userType;
            this.clientId = clientId; this.xuid = xuid;
            this.versionType = versionType; this.nativesDir = nativesDir;
            this.classpathStr = classpathStr; this.launcherName = launcherName;
            this.launcherVersion = launcherVersion;
            this.width = width; this.height = height;
        }
    }

    public boolean demo        = false;
    public boolean quickPlay   = false;
    public String  quickPlayMode = null;  
    public String  quickPlayValue = null;

    private final VersionInfo   versionInfo;
    private final LaunchContext ctx;

    public ArgumentResolver configureFromRequest(dev.novastep.core.server.LaunchRequest req) {
        if (req.features != null) {
            this.demo      = req.features.isDemoMode();
            this.quickPlay = req.features.hasQuickPlay();
            if (req.features.quickPlay != null) {
                this.quickPlayMode  = req.features.quickPlay.mode;
                this.quickPlayValue = req.features.quickPlay.value;
            }
        }
        return this;
    }

    public ArgumentResolver(VersionInfo versionInfo, LaunchContext ctx) {
        this.versionInfo = versionInfo;
        this.ctx         = ctx;
    }

    public List<String> resolveJvmArgs() {
        List<String> args = new ArrayList<>();
        if (versionInfo.arguments != null && versionInfo.arguments.jvm != null) {
            processArgList(versionInfo.arguments.jvm, args, false);
        } else {
            
            args.add("-Djava.library.path=" + ctx.nativesDir);
            args.add("-Dminecraft.launcher.brand=" + ctx.launcherName);
            args.add("-Dminecraft.launcher.version=" + ctx.launcherVersion);
            
        }
        return args;
    }

    public List<String> resolveGameArgs() {
        List<String> args = new ArrayList<>();
        if (versionInfo.arguments != null && versionInfo.arguments.game != null) {
            processArgList(versionInfo.arguments.game, args, true);
        } else if (versionInfo.minecraftArguments != null) {
            
            for (String a : versionInfo.minecraftArguments.split(" ")) {
                if (!a.isBlank()) args.add(substitute(a));
            }
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private void processArgList(List<Object> list, List<String> out, boolean isGame) {
        for (Object item : list) {
            if (item instanceof String s) {
                
                if (isGame && shouldSkipGameArg(s)) continue;
                out.add(substitute(s));

            } else if (item instanceof JsonElement je) {
                if (je.isJsonPrimitive()) {
                    String s = je.getAsString();
                    if (isGame && shouldSkipGameArg(s)) continue;
                    out.add(substitute(s));
                } else if (je.isJsonObject()) {
                    processConditionalArg(je.getAsJsonObject(), out, isGame);
                }

            } else if (item instanceof Map<?,?> map) {
                processConditionalArgMap((Map<String,Object>) map, out, isGame);
            }
        }
    }

    private void processConditionalArg(JsonObject obj, List<String> out, boolean isGame) {
        if (obj.has("rules") && !evaluateRulesJson(obj.getAsJsonArray("rules"))) return;
        if (!obj.has("value")) return;
        JsonElement val = obj.get("value");
        if (val.isJsonPrimitive()) {
            String s = val.getAsString();
            if (!isGame || !shouldSkipGameArg(s)) out.add(substitute(s));
        } else if (val.isJsonArray()) {
            for (JsonElement e : val.getAsJsonArray()) {
                String s = e.getAsString();
                if (!isGame || !shouldSkipGameArg(s)) out.add(substitute(s));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processConditionalArgMap(Map<String,Object> map, List<String> out, boolean isGame) {
        if (map.containsKey("rules")) {
            Object rulesObj = map.get("rules");
            if (rulesObj instanceof List<?> rules && !evaluateRulesList((List<Object>) rules)) return;
        }
        Object value = map.get("value");
        if (value instanceof String s) {
            if (!isGame || !shouldSkipGameArg(s)) out.add(substitute(s));
        } else if (value instanceof List<?> list) {
            for (Object v : list) {
                String s = String.valueOf(v);
                if (!isGame || !shouldSkipGameArg(s)) out.add(substitute(s));
            }
        }
    }

    private boolean evaluateRulesJson(JsonArray rules) {
        boolean result = false;
        for (JsonElement ruleEl : rules) {
            if (!ruleEl.isJsonObject()) continue;
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.has("action") ? rule.get("action").getAsString() : "allow";

            boolean matches;
            if (rule.has("features")) {
                matches = evaluateFeaturesJson(rule.getAsJsonObject("features"));
            } else if (rule.has("os")) {
                matches = evaluateOsJson(rule.getAsJsonObject("os"));
            } else {
                matches = true; 
            }
            if (matches) result = "allow".equals(action);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateRulesList(List<Object> rules) {
        boolean result = false;
        for (Object ruleObj : rules) {
            if (!(ruleObj instanceof Map)) continue;
            Map<String,Object> rule = (Map<String,Object>) ruleObj;
            String action = (String) rule.getOrDefault("action", "allow");

            boolean matches;
            if (rule.containsKey("features")) {
                matches = evaluateFeaturesMap((Map<String,Object>) rule.get("features"));
            } else if (rule.containsKey("os")) {
                matches = evaluateOsMap((Map<String,Object>) rule.get("os"));
            } else {
                matches = true;
            }
            if (matches) result = "allow".equals(action);
        }
        return result;
    }

    private boolean evaluateOsJson(JsonObject os) {
        String name = os.has("name") ? os.get("name").getAsString() : null;
        String arch = os.has("arch") ? os.get("arch").getAsString() : null;
        return osNameMatches(name) && osArchMatches(arch);
    }

    private boolean evaluateOsMap(Map<String,Object> os) {
        String name = (String) os.get("name");
        String arch = (String) os.get("arch");
        return osNameMatches(name) && osArchMatches(arch);
    }

    private boolean evaluateFeaturesJson(JsonObject features) {
        
        if (features.has("has_custom_resolution")) return true;
        
        if (features.has("is_demo_user"))
            return features.get("is_demo_user").getAsBoolean() == demo;
        
        if (features.has("is_quick_play_singleplayer"))
            return "singleplayer".equals(quickPlayMode);
        if (features.has("is_quick_play_multiplayer"))
            return "multiplayer".equals(quickPlayMode);
        if (features.has("is_quick_play_realms"))
            return "realms".equals(quickPlayMode);
        if (features.has("has_quick_plays_support"))
            return quickPlay;
        return true;
    }

    private boolean evaluateFeaturesMap(Map<String,Object> features) {
        if (features.containsKey("has_custom_resolution")) return true;
        if (features.containsKey("is_demo_user")) return ((Boolean)features.get("is_demo_user")) == demo;
        if (features.containsKey("is_quick_play_singleplayer")) return "singleplayer".equals(quickPlayMode);
        if (features.containsKey("is_quick_play_multiplayer")) return "multiplayer".equals(quickPlayMode);
        if (features.containsKey("is_quick_play_realms")) return "realms".equals(quickPlayMode);
        if (features.containsKey("has_quick_plays_support")) return quickPlay;
        return true;
    }

    private static boolean osNameMatches(String name) {
        if (name == null) return true;
        String current = System.getProperty("os.name", "").toLowerCase();
        return switch (name) {
            case "windows"-> current.contains("win");
            case "osx" -> current.contains("mac");
            case "linux" -> !current.contains("win") && !current.contains("mac");
            default -> false;
        };
    }

    private static boolean osArchMatches(String arch) {
        if (arch == null) return true;
        String current = System.getProperty("os.arch", "").toLowerCase();
        return switch (arch) {
            case "x86" -> current.equals("x86") || current.equals("i386") || current.equals("i686");
            case "x64" -> current.equals("amd64") || current.equals("x86_64");
            case "arm64" -> current.equals("aarch64") || current.equals("arm64");
            default -> current.contains(arch);
        };
    }

    private static final Set<String> DEMO_ARGS = Set.of("--demo");
    private static final Set<String> QUICKPLAY_ARGS = Set.of(
        "--quickPlaySingleplayer", "--quickPlayMultiplayer",
        "--quickPlayRealms", "--quickPlayPath"
    );

    private boolean shouldSkipGameArg(String arg) {
        if (DEMO_ARGS.contains(arg) && !demo) return true;
        if (QUICKPLAY_ARGS.contains(arg) && !quickPlay) return true;
        return false;
    }

    public String substitute(String template) {
        return template
            .replace("${auth_player_name}",   ctx.username)
            .replace("${version_name}",        ctx.version)
            .replace("${game_directory}",      ctx.gameDir)
            .replace("${assets_root}",         ctx.assetsDir)
            .replace("${game_assets}",         ctx.assetsDir)
            .replace("${assets_index_name}",   ctx.assetIndex)
            .replace("${auth_uuid}",           ctx.uuid)
            .replace("${auth_access_token}",   ctx.accessToken)
            .replace("${auth_session}",        ctx.accessToken)
            .replace("${user_type}",           ctx.userType)
            .replace("${user_properties}",     "{}")
            .replace("${auth_xuid}",           ctx.xuid)
            .replace("${clientid}",            ctx.clientId)
            .replace("${version_type}",        ctx.versionType)
            .replace("${natives_directory}",   ctx.nativesDir)
            .replace("${launcher_name}",       ctx.launcherName)
            .replace("${launcher_version}",    ctx.launcherVersion)
            .replace("${classpath}",           ctx.classpathStr)
            .replace("${classpath_separator}", File.pathSeparator)
            .replace("${library_directory}",   ctx.classpathStr) 
            .replace("${resolution_width}",    String.valueOf(ctx.width))
            .replace("${resolution_height}",   String.valueOf(ctx.height));
    }
}