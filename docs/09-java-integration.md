# Integración con Java

NovaCore-Engine es básicamente un servidor HTTP + WebSocket. Podés controlarlo desde cualquier lenguaje que soporte HTTP y WebSocket, no solo Node.js.

Acá te muestro cómo integrarlo desde Java puro, por ejemplo si estás construyendo un launcher con JavaFX o Swing.

---

## Requisitos

Para el cliente Java solo necesitás:
- Java 11+ (para `java.net.http.HttpClient`)
- Una librería de WebSocket: `tyrus-standalone-client` o `Java-WebSocket` (la misma que usa el engine internamente)

Si usás Gradle, agregá en tus dependencias:

```groovy
// Para WebSocket (Java-WebSocket)
implementation 'org.java-websocket:Java-WebSocket:1.5.4'

// Gson para JSON (opcional pero cómodo)
implementation 'com.google.code.gson:gson:2.10.1'
```

---

## Arrancar el engine desde Java

```java
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class NovaCoreProcess {

    private Process process;

    public void start(String jarPath, String instancesDir, String logDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "java",
            "-Xms32m", "-Xmx128m", "-XX:+UseG1GC",
            "-jar", jarPath,
            "--port",          "7878",
            "--ws-port",       "7879",
            "--threads",       "0",
            "--instances-dir", instancesDir,
            "--log-dir",       logDir,
            "--launcher-name", "MiLauncher",
            "--log-level",     "INFO"
        );

        pb.redirectErrorStream(false);
        process = pb.start();

        // Esperar a que imprima "Ready"
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );

        String line;
        long timeout = System.currentTimeMillis() + 20_000;
        while ((line = reader.readLine()) != null) {
            System.out.println("[Core] " + line);
            if (line.contains("Ready")) break;
            if (System.currentTimeMillis() > timeout) {
                throw new RuntimeException("Engine no arrancó en 20 segundos");
            }
        }

        System.out.println("Engine listo. PID: " + process.pid());
    }

    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }
}
```

---

## Cliente HTTP en Java

`java.net.http.HttpClient` (Java 11+) es suficiente para hablar con la API:

```java
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class NovaCoreClient {

    private static final String BASE_URL = "http://localhost:7878";
    private static final Gson   GSON     = new Gson();

    private final HttpClient http;

    public NovaCoreClient() {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    // GET genérico
    public String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .GET()
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new RuntimeException("GET " + path + " → " + res.statusCode());
        return res.body();
    }

    // POST genérico
    public String post(String path, Object body) throws Exception {
        String json = GSON.toJson(body);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new RuntimeException("POST " + path + " → " + res.statusCode());
        return res.body();
    }

    // Consultar info de la API
    public String apiInfo() throws Exception {
        return get("/api");
    }

    // Consultar recursos del sistema
    public String systemResources() throws Exception {
        return get("/system/resources");
    }

    // Listar versiones
    public String versions(String type) throws Exception {
        return get("/versions" + (type != null ? "?type=" + type : ""));
    }

    // Iniciar instalación
    public String install(String version, String instancePath, String sharedPath) throws Exception {
        Map<String, Object> body = Map.of(
            "version",      version,
            "instancePath", instancePath,
            "sharedPath",   sharedPath != null ? sharedPath : "",
            "download",     Map.of("client", true, "libraries", true, "assets", true, "natives", true),
            "verifySHA1",   true,
            "maxThreads",   0
        );
        return post("/install", body);
    }

    // Consultar progreso de una sesión
    public String progress(String sessionId) throws Exception {
        return get("/progress?sessionId=" + sessionId);
    }

    // Lanzar Minecraft
    public String launch(String version, String instancePath, String username) throws Exception {
        Map<String, Object> body = Map.of(
            "version",      version,
            "instancePath", instancePath,
            "auth", Map.of(
                "username", username,
                "userType", "offline"
            )
        );
        return post("/launch", body);
    }
}
```

---

## Conectar al WebSocket en Java

Usando `Java-WebSocket`:

```java
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.google.gson.*;
import java.net.URI;

public class NovaCoreWebSocket extends WebSocketClient {

    private static final Gson GSON = new Gson();

    public NovaCoreWebSocket() throws Exception {
        super(new URI("ws://localhost:7879"));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("[WS] Conectado al engine");
    }

    @Override
    public void onMessage(String raw) {
        try {
            JsonObject msg   = GSON.fromJson(raw, JsonObject.class);
            String eventType = msg.get("event").getAsString();
            JsonObject data  = msg.getAsJsonObject("data");
            handleEvent(eventType, data);
        } catch (Exception e) {
            System.err.println("[WS] Error parseando mensaje: " + e.getMessage());
        }
    }

    private void handleEvent(String event, JsonObject data) {
        switch (event) {
            case "connected" -> {
                System.out.println("[WS] Engine listo: " + data.get("version").getAsString());
            }
            case "session_progress" -> {
                int pct  = data.get("percent").getAsInt();
                int done = data.get("completedFiles").getAsInt()
                         + data.get("skippedFiles").getAsInt();
                int total = data.get("totalFiles").getAsInt();
                System.out.printf("\rProgreso: %3d%% — %d/%d archivos", pct, done, total);
            }
            case "session_completed" -> {
                System.out.println("\nInstalación completa: " + data.get("session").getAsString());
            }
            case "session_failed" -> {
                System.err.println("\nInstalación fallida: " + data.get("reason").getAsString());
            }
            case "game_log" -> {
                System.out.println("[MC] " + data.get("line").getAsString());
            }
            case "game_exited" -> {
                int exitCode = data.get("exitCode").getAsInt();
                String status = data.get("status").getAsString();
                System.out.printf("Minecraft cerró. Status: %s (código %d)%n", status, exitCode);
            }
            default -> {
                // podés loggear o ignorar los demás eventos
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[WS] Desconectado: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[WS] Error: " + ex.getMessage());
    }
}
```

---

## Ejemplo completo de uso en Java

```java
import com.google.gson.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {
        String JAR_PATH      = "./novacore-engine.jar";
        String INSTANCES_DIR = "./instances";
        String LOG_DIR       = "./logs";
        String VERSION       = "1.21.1";
        String INSTANCE_PATH = "./instances/mi-instancia";
        String SHARED_PATH   = "./shared";

        // 1. Arrancar el engine
        NovaCoreProcess proc = new NovaCoreProcess();
        proc.start(JAR_PATH, INSTANCES_DIR, LOG_DIR);

        Runtime.getRuntime().addShutdownHook(new Thread(proc::stop));

        // 2. Conectar el WebSocket
        NovaCoreWebSocket ws = new NovaCoreWebSocket();
        ws.connectBlocking(5, TimeUnit.SECONDS);

        // 3. Crear el cliente HTTP
        NovaCoreClient client = new NovaCoreClient();

        // 4. Ver info de la API
        System.out.println("API Info: " + client.apiInfo());

        // 5. Iniciar instalación
        String installResponse = client.install(VERSION, INSTANCE_PATH, SHARED_PATH);
        JsonObject installJson = new Gson().fromJson(installResponse, JsonObject.class);
        String sessionId = installJson.get("sessionId").getAsString();
        System.out.println("Sesión: " + sessionId);

        // 6. Polling de progreso (alternativa al WebSocket)
        while (true) {
            String progressStr = client.progress(sessionId);
            JsonObject snap = new Gson().fromJson(progressStr, JsonObject.class);
            String status = snap.get("status").getAsString();
            int pct = snap.get("overallPercent").getAsInt();
            System.out.printf("\r%3d%% — %s", pct, status);

            if ("completed".equals(status)) { System.out.println("\nListo!"); break; }
            if ("failed".equals(status))    { System.out.println("\nFalló"); break; }

            Thread.sleep(800);
        }

        // 7. Lanzar Minecraft
        String launchResponse = client.launch(VERSION, INSTANCE_PATH, "Jugador");
        JsonObject launchJson = new Gson().fromJson(launchResponse, JsonObject.class);
        String launchId = launchJson.get("launchId").getAsString();
        System.out.println("launchId: " + launchId);

        // Los logs del juego llegan por WebSocket (ya configurado en NovaCoreWebSocket.handleEvent)
        // Esperás hasta que game_exited llegue o manejás el shutdown vos

        // 8. Limpiar
        ws.closeBlocking();
        proc.stop();
    }
}
```

---

## Diferencias clave respecto al cliente Node.js

| Aspecto | Node.js | Java |
|---|---|---|
| `CoreProcess` | Clase lista para usar | Tenés que hacer el spawn a mano |
| `CoreClient` | Métodos tipados por endpoint | Hacés las requests HTTP manualmente |
| Tipos | `.d.ts` completo | Tenés que definir tus POJOs |
| Eventos WS | `EventEmitter` automático | Manejás el switch en `onMessage` |
| `waitForInstall` | Helper incluido | Hacés el polling o event listener vos |

La idea es la misma — la API HTTP y los eventos WebSocket son idénticos sin importar el lenguaje. La diferencia es que el cliente Node.js te da una abstracción lista, mientras que desde Java hacés la integración más a mano.
