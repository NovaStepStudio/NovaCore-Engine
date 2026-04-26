package dev.novastep.core.minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.novastep.core.log.CoreLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionManager {

    private static final String LOG = "SessionManager";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path sessionsFile;
    private static final AtomicBoolean privacyEnabled = new AtomicBoolean(false);
    private static List<Map<String, Object>> sessions = new ArrayList<>();

    public static void init(Path rootDir) {
        sessionsFile = rootDir.resolve("sessions.json");
        loadSessions();
    }

    public static void setPrivacyEnabled(boolean enabled) {
        privacyEnabled.set(enabled);
        if (!enabled) {
            CoreLogger.get().info("Privacy", "Tracking de sesiones deshabilitado por configuración");
            sessions.clear();
            saveSessions();
        } else {
            CoreLogger.get().info("Privacy", "Tracking de sesiones habilitado");
        }
    }

    public static boolean isPrivacyEnabled() {
        return privacyEnabled.get();
    }

    private static void loadSessions() {
        if (sessionsFile == null || !Files.exists(sessionsFile))
            return;
        try {
            String json = Files.readString(sessionsFile);
            sessions = GSON.fromJson(json, new TypeToken<List<Map<String, Object>>>() {
            }.getType());
            if (sessions == null)
                sessions = new ArrayList<>();
        } catch (IOException e) {
            CoreLogger.get().warn(LOG, "Could not load sessions: " + e.getMessage());
        }
    }

    private static void saveSessions() {
        if (sessionsFile == null)
            return;
        try {
            Files.writeString(sessionsFile, GSON.toJson(sessions));
        } catch (IOException e) {
            CoreLogger.get().warn(LOG, "Could not save sessions: " + e.getMessage());
        }
    }

    public static void recordSession(Map<String, Object> sessionData) {
        if (!privacyEnabled.get())
            return;

        sessions.add(sessionData);
        if (sessions.size() > 100) {
            sessions.remove(0);
        }
        saveSessions();
    }

    public static List<Map<String, Object>> getSessions() {
        if (!privacyEnabled.get())
            return new ArrayList<>();
        return sessions;
    }
}
