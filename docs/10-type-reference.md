# Referencia de Tipos TypeScript

Todos los tipos del cliente están definidos en `client/minecraft-core.d.ts`. Esta página es una referencia anotada de los más relevantes.

---

## InstallOptions

```ts
interface InstallOptions {
  version:           string;   // ID de versión ("1.21.6", "1.20.4", etc.)
  instancePath:      string;   // Ruta absoluta al directorio de la instancia
  sharedPath?:       string;   // Para compartir assets/libs entre instancias
  download?: {
    client?:         boolean;  // Default true
    libraries?:      boolean;  // Default true
    assets?:         boolean;  // Default true
    natives?:        boolean;  // Default true
    jvm?:            boolean;  // Default false — descarga el JDK de Mojang
  };
  verifySHA1?:       boolean;  // Verifica SHA1 antes de saltear archivos, default true
  maxThreads?:       number;   // Hilos de descarga paralela, default 4
  modloader?:        string;   // forge | neoforge | fabric | quilt | legacyfabric | optifine
  modloaderVersion?: string;   // Si no se pone, resuelve latest para la MC version dada
}
```

---

## LaunchOptions

```ts
interface LaunchOptions {
  version:               string;  // ID de versión, incluyendo el prefijo del modloader si aplica
  instancePath:          string;
  sharedPath?:           string;
  javaPath?:             string;  // Path absoluto al ejecutable java. Si no se pone, el engine lo resuelve
  hardwareAcceleration?: boolean;
  gcPreset?:             'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
  gpuPreference?:        'auto' | 'dgpu' | 'igpu';
  auth?:                 AuthConfig;
  authlibInjector?:      AuthlibInjectorConfig;
  jvm?:                  JvmConfig;
  window?:               WindowConfig;
  launcher?:             LauncherBranding;
  features?:             LaunchFeatures;
  game?:                 GameCustomization;
}
```

---

## AuthConfig

```ts
interface AuthConfig {
  username?:    string;                        // Nombre de usuario
  uuid?:        string;                        // UUID en formato estándar con guiones
  accessToken?: string;                        // Token MSA o "0" para offline
  userType?:    'msa' | 'legacy' | 'offline';  // Default "msa"
  clientId?:    string;                        // Client ID de la app MSA
  xuid?:        string;                        // XUID de Xbox Live
}
```

---

## JvmConfig

```ts
interface JvmConfig {
  minMemoryMb?:  number;    // RAM mínima en MB (el engine clampea si es muy bajo)
  maxMemoryMb?:  number;    // RAM máxima en MB
  extraArgs?:    string[];  // Argumentos JVM extra, se agregan al final
  prependArgs?:  string[];  // Argumentos que van antes de los generados por el engine
}
```

---

## SessionSnapshot

Estado de una sesión de descarga. Lo recibís tanto por WebSocket como por polling en `/progress`.

```ts
interface SessionSnapshot {
  sessionId:       string;
  status:          'pending' | 'running' | 'completed' | 'failed';
  createdAt:       number;   // Unix timestamp ms
  totalFiles:      number;
  completedFiles:  number;
  skippedFiles:    number;   // Archivos que ya existían y pasaron verificación
  failedFiles:     number;
  pendingFiles:    number;
  overallPercent:  number;   // 0-100
  downloadedBytes: number;
  totalBytes:      number;
  error?:          string;   // Solo presente si status === 'failed'
}
```

---

## InstalledLoaderState

El estado que persiste en disco después de instalar un modloader. Se puede consultar con `getModLoaderState(instancePath)`.

```ts
interface InstalledLoaderState {
  loaderType:       string;        // forge | fabric | quilt | etc.
  loaderVersion:    string;        // Versión del loader (ej: "56.0.9", "0.18.6")
  minecraftVersion: string;        // Versión de MC base (ej: "1.21.6")
  versionJsonId:    string;        // ID del version.json instalado (ej: "1.21.6-forge-56.0.9")
  installerJarPath: string | null; // Path al installer .jar, null para loaders sin installer
  installedAt:      number;        // Unix timestamp ms
}
```

---

## CoreClient — constructor

```ts
new CoreClient({
  accessToken:    string;   // Token generado por CoreProcess al arrancar el engine
  host?:          string;   // Default "localhost"
  httpPort?:      number;   // Default 7878
  wsPort?:        number;   // Default 7879
  maxReconnects?: number;   // 0 = infinito (default)
})
```

---

## CoreClient — getters

```ts
client.state     // 'disconnected' | 'connecting' | 'connected' | 'reconnecting'
client.connected // true si el WS está activo y autenticado
```

---

## CoreClient — eventos

Todos los eventos que emite el engine por WebSocket, con sus firmas:

```ts
// Conectividad
client.on('connectivity:change', (online: boolean) => void)
client.on('ws:connected',        () => void)
client.on('ws:disconnected',     (data: { code: number; reason: string }) => void)
client.on('offline:install',     (data: { version: string }) => void)
client.on('offline:launch',      (data: { username: string }) => void)

// Instalación
client.on('install_step',  (data: { sessionId: string; step: string; [k: string]: unknown }) => void)
client.on('tasks_ready',   (data: { sessionId: string; totalTasks: number; offline: boolean; breakdown: Record<string, number> }) => void)
client.on('offline_mode',  (data: { sessionId: string; version: string; reason: string }) => void)

// Sesiones de descarga
client.on('session_progress',  (data: SessionSnapshot) => void)
client.on('session_completed', (data: SessionSnapshot) => void)
client.on('session_failed',    (data: { sessionId: string; reason: string }) => void)

// Launch — ciclo de vida
client.on('launch_preparing',            (data: { launchId: string; version: string }) => void)
client.on('launch_verification_failed',  (data: { launchId: string; missing: string[]; hint: string }) => void)
client.on('launch_starting',             (data: { launchId: string; mainClass: string; version: string }) => void)
client.on('launch_started',              (data: { launchId: string; pid: number }) => void)
client.on('launch_exited',               (data: { launchId: string; exitCode: number }) => void)
client.on('launch_failed',               (data: { launchId: string; error: string }) => void)
client.on('launch_log_file',             (data: { launchId: string; logFile: string }) => void)

// Log del juego
client.on('game_stdout', (data: { launchId: string; line: string }) => void)
client.on('game_stderr', (data: { launchId: string; line: string }) => void)
client.on('game_log',    (data: { launchId: string; line: string; stream: 'stdout' | 'stderr' }) => void)

// Modloaders
client.on('modloader_resolving',      (data: { sessionId: string; loader: string; loaderVersion: string; mcVersion: string }) => void)
client.on('modloader_downloading',    (data: { sessionId: string; loader: string; files: number }) => void)
client.on('modloader_processor',      (data: { sessionId: string; step: number; total: number; jar: string }) => void)
client.on('modloader_install_start',  (data: { sessionId: string; loader: string; version: string }) => void)
client.on('modloader_install_done',   (data: { sessionId: string; loader: string; versionId: string }) => void)
client.on('modloader_installed',      (data: { sessionId: string; loader: string; loaderVersion: string; versionJsonId: string }) => void)
```

---

## CoreProcess

```ts
const proc = new CoreProcess({
  jarPath:      './novacore-engine.jar',
  port?:        7878,    // Puerto HTTP, default 7878
  wsPort?:      7879,    // Puerto WebSocket, default 7879
  javaPath?:    'java',  // Ejecutable para lanzar el engine
  maxMemoryMb?: 256,     // RAM del proceso del engine, no del juego
  debug?:       false,
});

const { accessToken, httpPort, wsPort } = await proc.start();

proc.isRunning();  // boolean
await proc.stop();

proc.port;    // Puerto HTTP activo
proc.wsPort;  // Puerto WS activo
```

`start()` spawna el JAR, espera a que el engine esté listo, y devuelve el `accessToken` que necesitás para construir el `CoreClient`. El token es generado fresco en cada arranque del engine.
