<div align="center">

# `@novastepstudios/novacore-engine-client`

**Cliente oficial de Node.js para [NovaCore-Engine](https://github.com/NovaStepStudios/NovaCore-Engine)**

[![Node.js](https://img.shields.io/badge/Node.js-18+-green?style=flat-square)](https://nodejs.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-purple?style=flat-square)](./LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-types%20incluidos-blue?style=flat-square)](./types/index.d.ts)

</div>

---

Cliente Node.js para controlar **NovaCore-Engine** — el motor Java de backend que maneja instalación, descarga y lanzamiento de Minecraft. Se conecta al engine por HTTP y WebSocket y expone toda la API como métodos async con tipos TypeScript completos.

## Requisitos

- **Node.js** 18+
- **NovaCore-Engine** corriendo (JAR compilado)

## Instalación

```bash
npm install @novastepstudios/novacore-engine-client
```

## Inicio rápido

```js
const { CoreProcess, CoreClient } = require('@novastepstudios/novacore-engine-client');

async function main() {
  // 1. Arrancar el engine
  const proc = new CoreProcess({
    jarPath:      './novacore-engine.jar',
    instancesDir: './instances',
    logDir:       './logs',
    launcherName: 'MiLauncher',
  });

  process.on('SIGINT', async () => { client.disconnect(); await proc.stop(); process.exit(0); });

  await proc.start();

  // 2. Conectar
  const client = new CoreClient();
  await client.connect();

  // 3. Instalar Minecraft
  const { sessionId } = await client.install({
    version:      '1.21.1',
    instancePath: './instances/mi-instancia',
    sharedPath:   './shared',
    download:     { client: true, libraries: true, assets: true, natives: true },
  });

  await client.waitForInstall(sessionId, (snap) => {
    process.stdout.write(`\r${snap.overallPercent}%`);
  });
  console.log('\n✓ Instalado');

  // 4. Lanzar
  const { launchId } = await client.launch({
    version:      '1.21.1',
    instancePath: './instances/mi-instancia',
    sharedPath:   './shared',
    auth:         { username: 'Jugador', userType: 'offline' },
    jvm:          { maxMemoryMb: 4096 },
    gcPreset:     'g1gc_optimized',
  });

  client.onGameLog(launchId, (line) => console.log('[MC]', line));
  const result = await client.waitForGame(launchId);
  console.log('Juego cerrado:', result.status);

  client.disconnect();
  await proc.stop();
}

main().catch(console.error);
```

---

## API

### `CoreProcess`

Gestiona el ciclo de vida del proceso Java del engine.

```js
const proc = new CoreProcess({
  jarPath:      './novacore-engine.jar', // Ruta al JAR
  javaPath:     'java',                  // Ejecutable Java (default: 'java')
  httpPort:     7878,                    // Puerto HTTP (default: 7878)
  wsPort:       7879,                    // Puerto WebSocket (default: 7879)
  threads:      0,                       // 0 = auto según CPU
  instancesDir: './instances',
  logDir:       './logs',
  launcherName: 'MiLauncher',
  logLevel:     'INFO',                  // DEBUG | INFO | WARN | ERROR
  verbose:      false,                   // re-emitir stdout como eventos 'log'
});

await proc.start();   // Spawnea el JAR y espera "Ready" (timeout: 20s)
await proc.stop();    // SIGTERM → SIGKILL si no cierra en 3s

proc.running  // boolean
proc.pid      // number | undefined

proc.on('ready',  () => {});
proc.on('log',    (line) => {});
proc.on('stderr', (line) => {});
proc.on('exit',   (code) => {});
```

### `CoreClient`

Cliente HTTP + WebSocket. Hereda de `EventEmitter`.

```js
const client = new CoreClient({
  host:     'localhost', // default: 'localhost'
  httpPort: 7878,        // default: 7878
  wsPort:   7879,        // default: 7879
});

await client.connect();    // Conecta WS y espera el evento 'connected'
client.disconnect();       // Cierra el WebSocket
```

#### Información

```js
client.apiInfo()          // → ApiInfoResponse
client.systemResources()  // → SystemResourcesResponse
client.versions(type?)    // type: 'release' | 'snapshot' | 'old_alpha' | 'old_beta'
```

#### Instalación

```js
client.install(opts)                              // → InstallResponse  (devuelve sessionId)
client.progress(sessionId)                        // → SessionSnapshot
client.allSessions()                              // → { count, sessions }
client.waitForInstall(sessionId, onProgress?)     // → SessionSnapshot  (espera a que termine)
```

#### Lanzamiento

```js
client.launch(opts)            // → LaunchResponse  (devuelve launchId)
client.killLaunch(launchId)    // → { launchId, status: 'killed' }
client.launchStatus(launchId)  // → { launchId, running, status }
client.waitForGame(launchId)   // → { launchId, exitCode, status: 'clean'|'crash' }
client.onGameLog(launchId, handler)  // → cleanup function
```

#### Instancias

```js
client.createInstance(opts)              // → CreateInstanceResponse
client.listInstances()                   // → { count, instances }
client.getInstance(idOrName)             // → InstanceInfo
client.updateInstance(idOrName, updates) // → { updated, id }
client.deleteInstance(idOrName)          // → { deleted, id }
```

#### Eventos

```js
// Tipado completo — devuelve cleanup function
const unsub = client.onEvent('session_progress', (data) => {
  console.log(data.percent);
});
unsub(); // dejar de escuchar

// EventEmitter estándar
client.on('game_log',      (data) => {});
client.once('game_exited', (data) => {});
client.off('game_log',     handler);

// Wildcard — todos los eventos
client.on('*', (eventName, data) => {});
```

---

## Opciones de instalación

| Campo | Tipo | Default | Descripción |
|---|---|---|---|
| `version` | `string` | — | Versión de Minecraft (`"1.21.1"`) |
| `instancePath` | `string` | — | Ruta al directorio de la instancia |
| `sharedPath` | `string` | `null` | Shared path para compartir assets/libs |
| `download.client` | `boolean` | `true` | Descargar el client.jar |
| `download.libraries` | `boolean` | `true` | Descargar librerías |
| `download.assets` | `boolean` | `true` | Descargar assets |
| `download.natives` | `boolean` | `true` | Descargar nativos |
| `download.jvm` | `boolean` | `false` | Descargar JVM de Mojang |
| `verifySHA1` | `boolean` | `true` | Verificar integridad SHA-1 |
| `maxThreads` | `number` | `0` | Threads de descarga. `0` = auto |

## Opciones de lanzamiento

| Campo | Tipo | Descripción |
|---|---|---|
| `version` | `string` | Versión a lanzar |
| `instancePath` | `string` | Ruta de la instancia |
| `auth.username` | `string` | Nombre de usuario |
| `auth.uuid` | `string` | UUID del jugador |
| `auth.accessToken` | `string` | Token MSA (o `"0"` para offline) |
| `auth.userType` | `'msa'\|'legacy'\|'offline'` | Tipo de auth |
| `jvm.minMemoryMb` | `number` | RAM mínima (0 = sin mínimo) |
| `jvm.maxMemoryMb` | `number` | RAM máxima (0 = auto) |
| `gcPreset` | `GcPreset` | `auto\|g1gc_basic\|g1gc_optimized\|zgc\|shenandoah` |
| `gpuPreference` | `'auto'\|'dgpu'\|'igpu'` | Preferencia de GPU |
| `window.width/height` | `number` | Tamaño de ventana |

## Ejemplos

```bash
# Configurar rutas en examples/config.js primero

npm run example:sysinfo    # Info del sistema y versiones
npm run example:instances  # CRUD de instancias
npm run example:install    # Instalar una versión
npm run example:launch     # Lanzar Minecraft
npm run example:advanced   # Lanzamiento con todas las opciones
npm run example:full       # Flujo completo: instalar + lanzar
```

## Eventos WebSocket

| Evento | Descripción |
|---|---|
| `connected` | Engine listo |
| `install_step` | Paso del proceso de instalación |
| `tasks_ready` | Total de archivos a descargar |
| `session_progress` | Progreso de la sesión (%) |
| `session_completed` | Instalación terminada |
| `session_failed` | Instalación fallida |
| `download_complete` | Archivo descargado |
| `launch_started` | Minecraft arrancó |
| `game_log` | Línea de log del juego |
| `game_exited` | Minecraft cerró (`clean` o `crash`) |
