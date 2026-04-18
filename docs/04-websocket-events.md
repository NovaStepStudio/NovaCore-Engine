# Eventos WebSocket

El engine emite eventos en tiempo real a través del WebSocket en `ws://localhost:7879`. Todos los mensajes tienen el mismo formato:

```json
{
  "event": "nombre_del_evento",
  "data": { ... }
}
```

El primer evento que recibís cuando te conectás es `connected`. A partir de ahí el engine emite todo lo que va pasando.

---

## Eventos de conexión

### `connected`

Se emite inmediatamente al conectarte al WebSocket.

```json
{
  "event": "connected",
  "data": {
    "message": "novacore-engine ready",
    "version": "1.0.0"
  }
}
```

---

## Eventos de instalación

### `install_step`

Indica en qué paso de la instalación está el engine.

```json
{
  "event": "install_step",
  "data": {
    "sessionId": "session-1710000000000-1",
    "step": "resolving_version",
    "version": "1.21.1"
  }
}
```

**Valores de `step`:**
| Paso | Descripción |
|---|---|
| `resolving_version` | Descargando el version manifest de Mojang |
| `fetching_asset_index` | Descargando el asset index |
| `downloading_jvm` | Descargando el JVM de Mojang |
| `building_task_list` | Construyendo la lista de archivos a descargar |
| `downloading` | Descargando archivos |
| `extracting_natives` | Extrayendo los nativos de la plataforma |

### `manifest_resolved`

Se emite cuando el version manifest fue descargado y procesado.

```json
{
  "event": "manifest_resolved",
  "data": {
    "sessionId": "session-1710000000000-1",
    "version": "1.21.1"
  }
}
```

### `tasks_ready`

Se emite cuando ya se sabe exactamente cuántos archivos hay que bajar.

```json
{
  "event": "tasks_ready",
  "data": {
    "sessionId": "session-1710000000000-1",
    "totalTasks": 312,
    "breakdown": {
      "client": 1,
      "libraries": 48,
      "assets": 280,
      "natives": 3,
      "asset_index": 1
    }
  }
}
```

---

## Eventos de sesión de descarga

### `session_started`

La sesión de descarga arrancó formalmente.

```json
{
  "event": "session_started",
  "data": {
    "session": "session-1710000000000-1",
    "totalFiles": 312,
    "totalBytes": 215000000
  }
}
```

### `session_progress`

Se emite periódicamente con el progreso general de la sesión. Es el evento que usás para actualizar tu barra de progreso.

```json
{
  "event": "session_progress",
  "data": {
    "session": "session-1710000000000-1",
    "completedFiles": 156,
    "skippedFiles": 25,
    "totalFiles": 312,
    "percent": 58,
    "downloadedBytes": 124000000,
    "totalBytes": 215000000
  }
}
```

### `session_completed`

La instalación terminó correctamente.

```json
{
  "event": "session_completed",
  "data": {
    "session": "session-1710000000000-1",
    "totalFiles": 312,
    "totalBytes": 215000000
  }
}
```

### `session_failed`

La instalación falló.

```json
{
  "event": "session_failed",
  "data": {
    "session": "session-1710000000000-1",
    "reason": "SocketTimeoutException: timeout connecting to resources.download.minecraft.net"
  }
}
```

---

## Eventos de descarga por archivo

Estos eventos son más granulares. Te permiten mostrar qué archivo se está bajando en este momento.

### `download_start`

```json
{
  "event": "download_start",
  "data": {
    "sessionId": "session-1710000000000-1",
    "category": "libraries",
    "file": "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar",
    "total": 264183
  }
}
```

### `download_progress`

```json
{
  "event": "download_progress",
  "data": {
    "sessionId": "session-1710000000000-1",
    "category": "libraries",
    "file": "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar",
    "downloaded": 131072,
    "total": 264183,
    "percent": 49
  }
}
```

### `download_complete`

```json
{
  "event": "download_complete",
  "data": {
    "sessionId": "session-1710000000000-1",
    "category": "libraries",
    "file": "com/google/code/gson/gson/2.10.1/gson-2.10.1.jar",
    "bytes": 264183,
    "skipped": false
  }
}
```

Si `skipped` es `true`, el archivo ya existía con el SHA-1 correcto y no se volvió a bajar.

### `download_error`

```json
{
  "event": "download_error",
  "data": {
    "sessionId": "session-1710000000000-1",
    "category": "assets",
    "file": "objects/00/00abc...",
    "error": "HttpTimeoutException: timeout"
  }
}
```

### `sha1_check`

Se emite cuando se verifica la integridad de un archivo existente.

```json
{
  "event": "sha1_check",
  "data": {
    "sessionId": "session-1710000000000-1",
    "file": "objects/00/00abc...",
    "passed": true,
    "expected": "00abc123...",
    "computed": "00abc123..."
  }
}
```

---

## Eventos del JVM (runtime de Java)

### `runtime_download_start`

```json
{
  "event": "runtime_download_start",
  "data": {
    "session": "session-1710000000000-1",
    "component": "java-runtime-gamma",
    "javaVersion": "21",
    "totalFiles": 487
  }
}
```

### `runtime_download_complete`

```json
{
  "event": "runtime_download_complete",
  "data": {
    "session": "session-1710000000000-1",
    "javaVersion": "21",
    "javaPath": "/home/user/.launcher/instances/mi-instancia/runtime/java-21/bin/java"
  }
}
```

### `runtime_ready`

Se emite cuando el JVM ya está disponible (ya sea porque se descargó o porque ya existía).

```json
{
  "event": "runtime_ready",
  "data": {
    "version": "21",
    "component": "java-runtime-gamma",
    "javaPath": "/home/user/.launcher/instances/mi-instancia/runtime/java-21/bin/java"
  }
}
```

### `runtime_error`

```json
{
  "event": "runtime_error",
  "data": {
    "version": "21",
    "error": "No runtime manifest found for this platform"
  }
}
```

---

## Eventos de lanzamiento

### `launch_preparing`

```json
{
  "event": "launch_preparing",
  "data": {
    "launchId": "launch-1710000000000-1",
    "version": "1.21.1"
  }
}
```

### `launch_command_ready`

Se emite justo antes de spawnear el proceso. Incluye el comando completo que se va a ejecutar — útil para debugging.

```json
{
  "event": "launch_command_ready",
  "data": {
    "launchId": "launch-1710000000000-1",
    "command": [
      "java", "-Xms512m", "-Xmx4096m",
      "-XX:+UseG1GC", "...",
      "-cp", "ruta/a/libs.jar:...",
      "net.minecraft.client.main.Main",
      "--username", "MiUsuario",
      "..."
    ],
    "mainClass": "net.minecraft.client.main.Main",
    "javaExec": "/usr/bin/java",
    "offline": false
  }
}
```

### `launch_started`

Minecraft arrancó correctamente.

```json
{
  "event": "launch_started",
  "data": {
    "launchId": "launch-1710000000000-1",
    "version": "1.21.1",
    "username": "MiUsuario",
    "gameDir": "/home/user/.launcher/instances/mi-instancia",
    "authlib": false,
    "javaExec": "/usr/bin/java",
    "offline": false
  }
}
```

### `launch_failed`

```json
{
  "event": "launch_failed",
  "data": {
    "launchId": "launch-1710000000000-1",
    "error": "FileNotFoundException: 1.21.1.jar not found. Did you install first?"
  }
}
```

### `game_log`

Se emite por cada línea que Minecraft escribe en su stdout. Así podés mostrar la consola del juego en tiempo real.

```json
{
  "event": "game_log",
  "data": {
    "launchId": "launch-1710000000000-1",
    "line": "[22:30:15] [Render thread/INFO]: Backend library: LWJGL version 3.3.3 build 7"
  }
}
```

### `game_exited`

El proceso de Minecraft cerró.

```json
{
  "event": "game_exited",
  "data": {
    "launchId": "launch-1710000000000-1",
    "exitCode": 0,
    "status": "clean"
  }
}
```

`status` puede ser `"clean"` (salida normal, código 0) o `"crash"` (código distinto de 0).

---

## Eventos de debug

### `debug`

Mensajes internos del engine. Solo aparecen si pasaste `debug: true` en el request de instalación, o para ciertos eventos del lanzamiento.

```json
{
  "event": "debug",
  "data": {
    "sessionId": "session-1710000000000-1",
    "message": "Classpath entries: 48"
  }
}
```

### `recovery_state`

Se emite al conectarse, enviando una lista de descargas reanudables (sesiones previas que quedaron interruptidas).

```json
{
  "event": "recovery_state",
  "data": {
    "count": 1,
    "snapshots": [
      {
        "sessionId": "session-1710000000000-1",
        "status": "paused",
        "overallPercent": 40
      }
    ]
  }
}
```

---

## Desconexión

### `ws:disconnected`

Este evento **no viene del servidor**. Es emitido por el `CoreClient` de Node.js cuando el WebSocket se cierra inesperadamente. No tiene `data`.

```js
client.on('ws:disconnected', () => {
  console.log('Se perdió la conexión con el engine');
});
```
