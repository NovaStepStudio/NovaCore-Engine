# Referencia de Tipos TypeScript

El archivo `client/minecraft-core.d.ts` define todos los tipos del cliente. Acá está la referencia completa con descripciones de cada campo.

---

## Opciones de instalación

```ts
interface InstallOptions {
  version:      string;       // ID de la versión (ej: "1.21.1", "1.20.4")
  instancePath: string;       // Ruta absoluta al directorio de la instancia
  sharedPath?:  string;       // Ruta para compartir assets/libs entre instancias
  download?: {
    client?:    boolean;      // Descargar el client.jar (default: true)
    libraries?: boolean;      // Descargar librerías (default: true)
    assets?:    boolean;      // Descargar assets (default: true)
    natives?:   boolean;      // Descargar nativos de la plataforma (default: true)
    jvm?:       boolean;      // Descargar JVM de Mojang (default: false)
  };
  verifySHA1?:  boolean;      // Verificar integridad de archivos existentes (default: true)
  maxThreads?:  number;       // Threads de descarga. 0 = auto (default: 0)
  debug?:       boolean;      // Emitir eventos debug adicionales (default: false)
}
```

---

## Opciones de lanzamiento

```ts
interface LaunchOptions {
  version:      string;       // Versión a lanzar
  instancePath: string;       // Ruta al directorio de la instancia
  sharedPath?:  string;       // Shared path (si instalaste con uno)
  javaPath?:    string;       // Ruta al ejecutable Java. null = usa el bundled o el del sistema

  auth?:            AuthConfig;
  authlibInjector?: AuthlibInjectorConfig;
  jvm?:             JvmConfig;
  window?:          WindowConfig;
  launcher?:        LauncherBranding;
  features?:        LaunchFeatures;
  game?:            GameCustomization;

  hardwareAcceleration?: boolean;   // Activar aceleración de hardware (default: false)
  gpuPreference?:        'auto' | 'dgpu' | 'igpu';  // Preferencia de GPU (default: 'auto')
  gcPreset?:             'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
}
```

---

## Autenticación

```ts
interface AuthConfig {
  username?:    string;                          // Nombre de usuario en el juego
  uuid?:        string;                          // UUID del perfil de Mojang
  accessToken?: string;                          // Token de acceso Microsoft
  userType?:    'msa' | 'legacy' | 'offline';   // Tipo de autenticación
  clientId?:    string;                          // Client ID del token MSA
  xuid?:        string;                          // XUID de Xbox Live
}

interface AuthlibInjectorConfig {
  enabled:   boolean;   // Si usar authlib-injector
  jarPath:   string;    // Ruta al JAR de authlib-injector
  serverUrl: string;    // URL del servidor de autenticación
}
```

---

## Configuración de JVM

```ts
interface JvmConfig {
  minMemoryMb?:  number;     // -Xms en MB. 0 = sin mínimo explícito
  maxMemoryMb?:  number;     // -Xmx en MB. 0 = auto según systemResources
  extraArgs?:    string[];   // Args JVM adicionales (al final)
  prependArgs?:  string[];   // Args JVM (antes de los resueltos por el engine)
}
```

---

## Ventana y branding

```ts
interface WindowConfig {
  width?:      number;    // Ancho de la ventana en píxeles
  height?:     number;    // Alto de la ventana en píxeles
  fullscreen?: boolean;   // Pantalla completa (default: false)
}

interface LauncherBranding {
  name?:    string;   // Nombre del launcher (aparece en el menú de Minecraft)
  version?: string;   // Versión del launcher
}
```

---

## Features y personalización del juego

```ts
interface LaunchFeatures {
  demo?:       boolean;     // Activar modo demo
  quickPlay?:  {
    mode:  'singleplayer' | 'multiplayer' | 'realms';
    value: string;          // Para multiplayer: dirección del servidor
  };
}

interface GameCustomization {
  gameDir?:             string;                    // Directorio del juego (saves, options, etc.)
  extraGameArgs?:       string[];                  // Args adicionales para el juego
  extraJvmProperties?:  Record<string, string>;    // Propiedades JVM (-Dprop=value)
  disableMultiplayer?:  boolean;                   // Deshabilitar botón de multijugador
  disableChat?:         boolean;                   // Deshabilitar chat
  serverHost?:          string;                    // Conectar a este servidor al arrancar
  serverPort?:          number;                    // Puerto del servidor
}
```

---

## Configuración de instancia

```ts
interface InstanceConfig {
  modLoader?:         'vanilla' | 'fabric' | 'forge' | 'neoforge' | 'quilt' | 'liteloader';
  modLoaderVersion?:  string;
  javaPath?:          string;
  minMemoryMb?:       number;
  maxMemoryMb?:       number;
  hardwareAccel?:     boolean;
  gcPreset?:          'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
  jvmArgs?:           string[];
  extraGameArgs?:     string[];
  jvmProperties?:     Record<string, string>;
  launcherName?:      string;
  launcherVersion?:   string;
  serverHost?:        string;
  serverPort?:        number;
  disableMultiplayer?: boolean;
  disableChat?:       boolean;
  customGameDir?:     string;
}

interface CreateInstanceOptions {
  name:         string;
  mcVersion:    string;
  config?:      InstanceConfig;
  autoInstall?: boolean;         // Si true, instala automáticamente al crear
  install?:     AutoInstallConfig; // Opciones de instalación si autoInstall: true
}

interface AutoInstallConfig {
  sharedPath?:  string;
  download?: {
    client?:    boolean;
    libraries?: boolean;
    assets?:    boolean;
    natives?:   boolean;
    jvm?:       boolean;
  };
  verifySHA1?:  boolean;
  maxThreads?:  number;
}
```

---

## Respuestas de la API

```ts
interface InstallResponse {
  sessionId:    string;
  version:      string;
  instancePath: string;
  status:       'started';
  progress:     string;   // URL para polling: "GET /progress?sessionId=..."
  websocket:    string;   // URL del WebSocket
}

interface LaunchResponse {
  launchId:        string;
  status:          'launching';
  version:         string;
  username:        string;
  instancePath:    string;
  authlibInjector: { enabled: boolean; server?: string };
  message:         string;
  kill:            string;   // Endpoint para matar: "POST /launch/kill/..."
}

interface CreateInstanceResponse {
  id:               string;
  name:             string;
  path:             string;
  installSessionId?: string;   // Solo si autoInstall: true
  installStatus?:   'started';
  installProgress?: string;
}

interface InstanceInfo {
  id:              string;
  name:            string;
  mcVersion:       string;
  modLoader:       string;
  modLoaderVersion?: string;
  minMemoryMb:     number;
  maxMemoryMb:     number;
  hardwareAccel:   boolean;
  gcPreset:        string | null;
  launcherName:    string | null;
  launcherVersion: string | null;
  serverHost:      string | null;
  serverPort:      number | null;
  jvmArgs:         string[];
  extraGameArgs:   string[];
  createdAt:       string;
  lastPlayedAt:    string | null;
  totalPlayHours:  string;
  path:            string;
  installed:       boolean;
}
```

---

## Sesiones y progreso

```ts
interface SessionSnapshot {
  sessionId:       string;
  status:          'pending' | 'running' | 'completed' | 'failed';
  createdAt:       number;    // timestamp en ms
  totalFiles:      number;
  completedFiles:  number;    // archivos descargados nuevos
  skippedFiles:    number;    // archivos reutilizados (ya existían con SHA-1 correcto)
  failedFiles:     number;
  pendingFiles:    number;
  totalBytes:      number;
  downloadedBytes: number;
  overallPercent:  number;    // 0-100
  error?:          string;
}
```

---

## Sistema y versiones

```ts
interface SystemResourcesResponse {
  cpu: {
    cores:             number;
    optimalDlThreads:  number;
  };
  ram: {
    totalMb:           number;
    estimatedFreeMb:   number;
    reservedForOsMb:   number;
  };
  recommended: {
    downloadThreads:   number;
    mcMinRamMb:        number;
    mcMaxRamMb:        number;
    gcPreset:          'g1gc_basic' | 'g1gc_optimized' | 'zgc';
  };
}

interface VersionEntry {
  id:          string;
  type:        'release' | 'snapshot' | 'old_alpha' | 'old_beta';
  releaseTime: string;   // ISO 8601
  url:         string;
}

interface VersionsResponse {
  latest:   { release: string; snapshot: string };
  count:    number;
  filter?:  string;
  versions: VersionEntry[];
}
```

---

## Eventos del WebSocket

```ts
interface CoreEvents {
  // Conexión
  'connected':               { message: string; version: string };

  // Instalación
  'install_step':            { sessionId: string; step: InstallStep; [key: string]: unknown };
  'manifest_resolved':       { sessionId: string; version: string };
  'tasks_ready':             { sessionId: string; totalTasks: number; breakdown: TaskBreakdown };
  'session_started':         { session: string; totalFiles: number; totalBytes: number };
  'session_progress':        SessionProgress;
  'session_completed':       { session: string; totalFiles: number; totalBytes: number };
  'session_failed':          { session: string; reason: string };

  // Descarga por archivo
  'download_start':          { sessionId: string; category: FileCategory; file: string; total: number };
  'download_progress':       { sessionId: string; category: FileCategory; file: string; downloaded: number; total: number; percent: number };
  'download_complete':       { sessionId: string; category: FileCategory; file: string; bytes: number; skipped: boolean };
  'download_error':          { sessionId: string; category: FileCategory; file: string; error: string };
  'sha1_check':              { sessionId: string; file: string; passed: boolean; expected: string; computed: string };

  // Runtime Java
  'runtime_download_start':    { session: string; component: string; javaVersion: string; totalFiles: number };
  'runtime_download_complete': { session: string; javaVersion: string; javaPath: string };
  'runtime_ready':             { version: string; component: string; javaPath: string };
  'runtime_error':             { version: string; error: string };

  // Lanzamiento
  'launch_preparing':        { launchId: string; version: string };
  'launch_command_ready':    { launchId: string; command: string[]; mainClass: string; javaExec: string; offline: boolean };
  'launch_started':          { launchId: string; version: string; username: string; gameDir: string; authlib: boolean; javaExec: string; offline: boolean };
  'launch_failed':           { launchId: string; error: string };
  'game_log':                { launchId: string; line: string };
  'game_exited':             { launchId: string; exitCode: number; status: 'clean' | 'crash' };

  // Debug / interno
  'debug':                   { sessionId: string; message: string };

  // Solo en el cliente Node.js (no viene del servidor)
  'ws:disconnected':         void;
}

type InstallStep =
  | 'resolving_version'
  | 'fetching_asset_index'
  | 'downloading_jvm'
  | 'building_task_list'
  | 'downloading'
  | 'extracting_natives';

type FileCategory = 'client' | 'library' | 'asset' | 'native' | 'asset_index' | 'runtime';

interface SessionProgress {
  session:         string;
  completedFiles:  number;
  skippedFiles:    number;
  totalFiles:      number;
  percent:         number;
  downloadedBytes: number;
  totalBytes:      number;
}

interface TaskBreakdown {
  client:      number;
  libraries:   number;
  assets:      number;
  natives:     number;
  asset_index: number;
}
```

---

## Usar los tipos en TypeScript

```ts
import type {
  InstallOptions,
  LaunchOptions,
  SessionSnapshot,
  CoreEvents,
  InstanceInfo,
} from './minecraft-core';

import { CoreClient, CoreProcess } from './minecraft-core';

const proc = new CoreProcess({ jarPath: './novacore-engine.jar' });
const client = new CoreClient();

await proc.start();
await client.connect();

// El tipado es completo en todos los métodos
const snap: SessionSnapshot = await client.progress('session-...');

// Los eventos también tienen tipo completo
client.on('session_progress', (data) => {
  // data es SessionProgress — TypeScript sabe qué campos tiene
  console.log(data.percent, data.completedFiles);
});

client.on('game_exited', (data) => {
  // data tiene launchId, exitCode, status
  if (data.status === 'crash') {
    console.error('Crash con código', data.exitCode);
  }
});
```
