# API HTTP

El engine expone una API REST en `http://localhost:7878` (el puerto es configurable). Todos los endpoints devuelven JSON. Los errores siempre incluyen un campo `error` con la descripción.

---

## GET /api

Información general de la API y lista de endpoints disponibles.

```bash
curl http://localhost:7878/api
```

**Respuesta:**
```json
{
  "name": "novacore-engine",
  "vendor": "NovaStepStudios",
  "version": "1.0.0",
  "java": "21.0.3",
  "os": "Linux",
  "endpoints": {
    "install": "POST /install",
    "launch": "POST /launch",
    "launch_kill": "POST /launch/kill/:id",
    "launch_status": "GET /launch/status/:id",
    "progress": "GET /progress?sessionId=...",
    "sessions": "GET /progress",
    "versions": "GET /versions",
    "runtime": "POST /runtime/download",
    "instances": "GET|POST /instances",
    "system": "GET /system/resources",
    "debug": {
      "client": "GET /debug/download/client",
      "libraries": "GET /debug/download/libraries",
      "assets": "GET /debug/download/assets",
      "natives": "GET /debug/download/natives"
    }
  }
}
```

---

## GET /system/resources

Detecta los recursos del sistema y da recomendaciones de configuración para Minecraft.

```bash
curl http://localhost:7878/system/resources
```

**Respuesta:**
```json
{
  "cpu": {
    "cores": 8,
    "optimalDlThreads": 16
  },
  "ram": {
    "totalMb": 16384,
    "estimatedFreeMb": 10240,
    "reservedForOsMb": 2048
  },
  "recommended": {
    "downloadThreads": 16,
    "mcMinRamMb": 512,
    "mcMaxRamMb": 4096,
    "gcPreset": "g1gc_optimized"
  }
}
```

---

## GET /versions

Lista las versiones de Minecraft disponibles desde el manifest de Mojang.

```bash
# Todas las versiones
curl http://localhost:7878/versions

# Solo releases
curl "http://localhost:7878/versions?type=release"

# Solo snapshots
curl "http://localhost:7878/versions?type=snapshot"
```

**Parámetros de query:**
| Parámetro | Valores | Descripción |
|---|---|---|
| `type` | `release`, `snapshot`, `old_alpha`, `old_beta` | Filtrar por tipo |

**Respuesta:**
```json
{
  "latest": {
    "release": "1.21.1",
    "snapshot": "24w14a"
  },
  "count": 50,
  "filter": "release",
  "versions": [
    {
      "id": "1.21.1",
      "type": "release",
      "releaseTime": "2024-08-08T12:00:00+00:00",
      "url": "https://..."
    }
  ]
}
```

---

## POST /install

Inicia la instalación de una versión de Minecraft. Devuelve un `sessionId` inmediatamente y procede en segundo plano. El progreso se puede seguir por WebSocket o polling en `/progress`.

```bash
curl -X POST http://localhost:7878/install \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.21.1",
    "instancePath": "/home/user/.launcher/instances/mi-instancia",
    "sharedPath": "/home/user/.launcher/shared",
    "download": {
      "client": true,
      "libraries": true,
      "assets": true,
      "natives": true,
      "jvm": false
    },
    "verifySHA1": true,
    "maxThreads": 0
  }'
```

**Body:**
| Campo | Tipo | Default | Descripción |
|---|---|---|---|
| `version` | `string` | — | ID de la versión a instalar (ej: `"1.21.1"`) |
| `instancePath` | `string` | — | Ruta al directorio de la instancia |
| `sharedPath` | `string \| null` | `null` | Ruta compartida para libs/assets entre instancias |
| `download.client` | `boolean` | `true` | Descargar el client.jar |
| `download.libraries` | `boolean` | `true` | Descargar librerías |
| `download.assets` | `boolean` | `true` | Descargar assets (sonidos, texturas) |
| `download.natives` | `boolean` | `true` | Descargar nativos de la plataforma |
| `download.jvm` | `boolean` | `false` | Descargar el JVM de Mojang para esta versión |
| `verifySHA1` | `boolean` | `true` | Verificar integridad de archivos existentes |
| `maxThreads` | `number` | `0` | Threads de descarga. `0` = auto según CPU |
| `debug` | `boolean` | `false` | Emitir eventos de debug detallados |

**Respuesta:**
```json
{
  "sessionId": "session-1710000000000-1",
  "version": "1.21.1",
  "instancePath": "/home/user/.launcher/instances/mi-instancia",
  "status": "started",
  "progress": "GET /progress?sessionId=session-1710000000000-1",
  "websocket": "ws://localhost:7879"
}
```

---

## GET /progress

Consulta el estado de una sesión de descarga.

```bash
# Una sesión específica
curl "http://localhost:7878/progress?sessionId=session-1710000000000-1"

# Todas las sesiones activas
curl http://localhost:7878/progress
```

**Respuesta (sesión específica):**
```json
{
  "sessionId": "session-1710000000000-1",
  "status": "completed",
  "createdAt": 1710000000000,
  "totalFiles": 312,
  "completedFiles": 287,
  "skippedFiles": 25,
  "failedFiles": 0,
  "pendingFiles": 0,
  "totalBytes": 215000000,
  "downloadedBytes": 215000000,
  "overallPercent": 100
}
```

**Respuesta (todas las sesiones):**
```json
{
  "count": 2,
  "sessions": [ ... ]
}
```

---

## POST /launch

Lanza Minecraft. Devuelve un `launchId` inmediatamente y el proceso corre en background. Los logs del juego llegan por WebSocket como eventos `game_log`.

```bash
curl -X POST http://localhost:7878/launch \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.21.1",
    "instancePath": "/home/user/.launcher/instances/mi-instancia",
    "auth": {
      "username": "MiUsuario",
      "uuid": "uuid-del-jugador",
      "accessToken": "token-de-microsoft",
      "userType": "msa"
    },
    "jvm": {
      "minMemoryMb": 512,
      "maxMemoryMb": 4096
    }
  }'
```

**Body completo:**
| Campo | Tipo | Default | Descripción |
|---|---|---|---|
| `version` | `string` | — | Versión a lanzar |
| `instancePath` | `string` | — | Ruta de la instancia |
| `sharedPath` | `string \| null` | `null` | Shared path (si las libs están ahí) |
| `javaPath` | `string \| null` | `null` | Ruta al ejecutable Java. `null` = usa el bundled de Mojang o el del sistema |
| `hardwareAcceleration` | `boolean` | `false` | Agrega args para aceleración de hardware |
| `gcPreset` | `string` | `"auto"` | Preset de GC: `auto`, `g1gc_basic`, `g1gc_optimized`, `zgc`, `shenandoah` |
| `gpuPreference` | `string` | `"auto"` | Preferencia de GPU: `auto`, `dgpu`, `igpu` |
| `auth.username` | `string` | `"Player"` | Nombre de usuario |
| `auth.uuid` | `string` | generado | UUID del jugador |
| `auth.accessToken` | `string` | `"0"` | Token de acceso (MSA, o `"0"` para offline) |
| `auth.userType` | `string` | `"msa"` | Tipo: `msa`, `legacy`, `offline` |
| `jvm.minMemoryMb` | `number` | `0` | RAM mínima (0 = auto) |
| `jvm.maxMemoryMb` | `number` | `0` | RAM máxima (0 = auto) |
| `jvm.extraArgs` | `string[]` | `[]` | Args JVM adicionales |
| `window.width` | `number` | — | Ancho de la ventana |
| `window.height` | `number` | — | Alto de la ventana |
| `window.fullscreen` | `boolean` | `false` | Pantalla completa |
| `launcher.name` | `string` | — | Nombre del launcher (para branding) |
| `launcher.version` | `string` | — | Versión del launcher |
| `game.serverHost` | `string` | — | Conectar directo a un servidor al arrancar |
| `game.serverPort` | `number` | — | Puerto del servidor |

**Respuesta:**
```json
{
  "launchId": "launch-1710000000000-1",
  "status": "launching",
  "version": "1.21.1",
  "username": "MiUsuario",
  "instancePath": "/home/user/.launcher/instances/mi-instancia",
  "authlibInjector": { "enabled": false },
  "message": "Minecraft launching in background",
  "kill": "POST /launch/kill/launch-1710000000000-1"
}
```

---

## GET /launch/status/:id

Consulta si un proceso de Minecraft sigue corriendo.

```bash
curl http://localhost:7878/launch/status/launch-1710000000000-1
```

**Respuesta:**
```json
{
  "launchId": "launch-1710000000000-1",
  "running": true,
  "status": "running"
}
```

---

## POST /launch/kill/:id

Termina un proceso de Minecraft en ejecución.

```bash
curl -X POST http://localhost:7878/launch/kill/launch-1710000000000-1
```

**Respuesta:**
```json
{
  "launchId": "launch-1710000000000-1",
  "status": "killed"
}
```

---

## GET /instances

Lista todas las instancias registradas.

```bash
curl http://localhost:7878/instances
```

**Respuesta:**
```json
{
  "count": 2,
  "instances": [
    {
      "id": "uuid-de-la-instancia",
      "name": "1.21.1-vanilla",
      "mcVersion": "1.21.1",
      "modLoader": "vanilla",
      "minMemoryMb": 512,
      "maxMemoryMb": 4096,
      "gcPreset": "g1gc_optimized",
      "createdAt": "2024-03-17T22:00:00Z",
      "lastPlayedAt": null,
      "totalPlayHours": "0h 0m",
      "installed": true,
      "path": "/home/user/.launcher/instances/1.21.1-vanilla"
    }
  ]
}
```

---

## POST /instances

Crea una nueva instancia (y opcionalmente la instala de una).

```bash
curl -X POST http://localhost:7878/instances \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mi-instancia",
    "mcVersion": "1.21.1",
    "config": {
      "modLoader": "vanilla",
      "minMemoryMb": 512,
      "maxMemoryMb": 4096,
      "gcPreset": "g1gc_optimized"
    },
    "autoInstall": false
  }'
```

Si `autoInstall` es `true`, también podés pasar un campo `install` con las opciones de instalación (mismas que `POST /install`).

---

## GET /instances/:idOrName

Obtiene los detalles de una instancia por ID o nombre.

```bash
curl http://localhost:7878/instances/mi-instancia
curl http://localhost:7878/instances/uuid-de-la-instancia
```

---

## PATCH /instances/:idOrName

Actualiza la configuración de una instancia.

```bash
curl -X PATCH http://localhost:7878/instances/mi-instancia \
  -H "Content-Type: application/json" \
  -d '{
    "maxMemoryMb": 8192,
    "gcPreset": "zgc"
  }'
```

---

## DELETE /instances/:idOrName

Borra una instancia del registro (no borra los archivos del disco).

```bash
curl -X DELETE http://localhost:7878/instances/mi-instancia
```

---

## POST /runtime/download

Descarga el runtime de Java (JVM) de Mojang para una versión específica.

```bash
curl -X POST http://localhost:7878/runtime/download \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.21.1",
    "instancePath": "/home/user/.launcher/instances/mi-instancia"
  }'
```

---

## GET /debug/download/:category

Devuelve el estado detallado de cada archivo dentro de una categoría para una sesión activa. Útil para debuggear problemas de instalación.

```bash
# Ver estado de todos los assets de una sesión
curl "http://localhost:7878/debug/download/assets?sessionId=session-1710000000000-1"

# Sin sessionId devuelve la sesión más reciente
curl http://localhost:7878/debug/download/client
```

**Categorías:** `client`, `libraries`, `assets`, `natives`

**Respuesta:**
```json
{
  "sessionId": "session-1710000000000-1",
  "category": "assets",
  "total": 280,
  "summary": {
    "done": 250,
    "skipped": 28,
    "failed": 0,
    "pending": 2,
    "downloading": 0
  },
  "files": [
    {
      "file": "objects/00/00abc123...",
      "status": "done",
      "size": 4096,
      "downloaded": 4096,
      "progress": 100,
      "destination": "/home/user/.launcher/shared/assets/objects/00/00abc123",
      "url": "https://resources.download.minecraft.net/..."
    }
  ]
}
```
