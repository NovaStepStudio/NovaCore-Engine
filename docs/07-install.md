# Instalación de Minecraft

El sistema de instalación se encarga de descargar todo lo que necesita Minecraft para correr: el cliente, las librerías, los assets, los nativos, y el runtime de Java. Todo corre en segundo plano — vos iniciás la instalación, obtenés un `sessionId`, y seguís el progreso por WebSocket o por polling.

---

## Iniciar una instalación

### Node.js

```js
const { sessionId } = await client.install({
  version:      '1.21.6',
  instancePath: '/home/user/.launcher/instances/mi-instancia',
  sharedPath:   '/home/user/.launcher/shared',
  download: {
    client:    true,
    libraries: true,
    assets:    true,
    natives:   true,
    jvm:       true,
  },
});
```

### HTTP

```bash
curl -X POST http://localhost:7878/install \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "version": "1.21.6",
    "instancePath": "/home/user/.launcher/instances/mi-instancia"
  }'
```

**Respuesta:**
```json
{ "sessionId": "session-1234567890-1" }
```

---

## Instalar con modloader

Podés incluir el modloader en la misma llamada de instalación. El engine primero instala vanilla, después corre el pipeline del modloader, y luego descarga las runtime libs del modloader que no vengan bundleadas en el installer. Todo en la misma sesión.

```js
const { sessionId } = await client.install({
  version:           '1.21.6',
  instancePath:      './instances/mi-instancia',
  modloader:         'forge',
  modloaderVersion:  '56.0.9',
});
```

Los modloaders soportados son `forge`, `neoforge`, `fabric`, `quilt`, `legacyfabric` y `optifine`. Si no especificás `modloaderVersion`, se resuelve el latest automáticamente.

Para modloaders que usan installer JAR (Forge, NeoForge), el pipeline corre los procesadores necesarios y después descarga cualquier librería de runtime que el installer no incluya bundleada. Esto es automático.

---

## Opciones

| Campo              | Tipo      | Default | Descripción                                                       |
|--------------------|-----------|---------|-------------------------------------------------------------------|
| `version`          | `string`  | —       | ID de versión de Minecraft (`"1.21.6"`, `"1.20.4"`, etc.)        |
| `instancePath`     | `string`  | —       | Ruta absoluta al directorio de la instancia                       |
| `sharedPath`       | `string`  | —       | Ruta para compartir assets y librerías entre instancias           |
| `download.client`  | `boolean` | `true`  | Descargar el client.jar                                           |
| `download.libraries` | `boolean` | `true` | Descargar librerías                                              |
| `download.assets`  | `boolean` | `true`  | Descargar assets                                                  |
| `download.natives` | `boolean` | `true`  | Descargar y extraer nativos                                       |
| `download.jvm`     | `boolean` | `false` | Descargar el JDK provisto por Mojang                              |
| `verifySHA1`       | `boolean` | `true`  | Verificar SHA1 de archivos existentes antes de saltearlos         |
| `maxThreads`       | `number`  | `4`     | Hilos de descarga paralela                                        |
| `modloader`        | `string`  | —       | Nombre del modloader a instalar                                   |
| `modloaderVersion` | `string`  | —       | Versión del modloader (si no se especifica, resuelve latest)      |

---

## Seguir el progreso

### Por WebSocket

```js
client.on('session_progress', (snap) => {
  if (snap.sessionId !== sessionId) return;
  const pct = snap.overallPercent.toFixed(1);
  console.log(`${pct}% — ${snap.completedFiles}/${snap.totalFiles} archivos`);
});

client.on('session_completed', (snap) => {
  if (snap.sessionId !== sessionId) return;
  console.log('Instalación completa');
});

client.on('session_failed', (data) => {
  if (data.sessionId !== sessionId) return;
  console.error('Falló:', data.reason);
});
```

También podés escuchar pasos específicos del proceso:

```js
client.on('install_step', (data) => {
  // data.step puede ser: resolving_version, fetching_asset_index,
  // downloading_jvm, building_task_list, downloading, extracting_natives, modloader
  console.log('Paso:', data.step);
});

client.on('modloader_processor', (data) => {
  console.log(`Procesador ${data.step}/${data.total}: ${data.jar}`);
});
```

### Con waitForInstall

Si no querés manejar los eventos manualmente, `waitForInstall` resuelve la promesa cuando termina la sesión:

```js
const snap = await client.waitForInstall(sessionId, (s) => {
  process.stdout.write(`\r${s.overallPercent.toFixed(1)}%`);
});

console.log(`Listo: ${snap.completedFiles} archivos, ${snap.skippedFiles} ya existían`);
```

### Por polling

```js
const snap = await client.progress(sessionId);
console.log(snap.overallPercent, snap.status);
```

---

## Modo offline

Si el engine no tiene conexión al arrancar una instalación, cae automáticamente a modo offline. En ese caso usa el caché local si existe. Si nunca instalaste esa versión antes, la instalación falla.

```js
client.on('offline_mode', (data) => {
  console.warn('Sin conexión, usando caché para', data.version);
});
```

---

## Estructura de archivos

Después de una instalación completa, la instancia queda organizada así:

```
mi-instancia/
├── versions/
│   └── 1.21.6/
│       ├── 1.21.6.jar
│       ├── 1.21.6.json
│       └── natives/
├── libraries/        ← o en sharedPath/libraries si se configuró
├── assets/           ← o en sharedPath/assets
├── runtime/
│   └── java-21.0.7/
│       └── bin/
│           └── java (o java.exe en Windows)
└── game/             ← directorio de trabajo del juego
```

Si instalaste un modloader con installer JAR (Forge, NeoForge), también vas a tener:

```
mi-instancia/
├── versions/
│   └── 1.21.6-forge-56.0.9/
│       └── 1.21.6-forge-56.0.9.json
└── installers/
    └── 1.21.6-56.0.9-installer.jar
```
