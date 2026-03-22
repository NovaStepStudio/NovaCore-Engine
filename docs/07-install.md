# Instalación de Minecraft

El sistema de instalación de NovaCore-Engine se encarga de descargar todo lo necesario para correr una versión de Minecraft: el cliente, las librerías, los assets, los nativos, y opcionalmente el runtime de Java.

Todo corre en segundo plano. Vos iniciás la instalación, obtenés un `sessionId`, y seguís el progreso por WebSocket o polling.

---

## Iniciar una instalación

### Node.js

```js
const { sessionId } = await client.install({
  version:      '1.21.1',
  instancePath: '/home/user/.launcher/instances/mi-instancia',
  sharedPath:   '/home/user/.launcher/shared',  // opcional
  download: {
    client:    true,
    libraries: true,
    assets:    true,
    natives:   true,
    jvm:       false,  // descargar JVM de Mojang
  },
  verifySHA1: true,
  maxThreads: 0,      // 0 = detecta automáticamente según CPU
});
```

### HTTP directo

```bash
curl -X POST http://localhost:7878/install \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.21.1",
    "instancePath": "/home/user/.launcher/instances/mi-instancia",
    "sharedPath": "/home/user/.launcher/shared",
    "download": {
      "client": true, "libraries": true, "assets": true, "natives": true
    },
    "verifySHA1": true,
    "maxThreads": 0
  }'
```

---

## Seguir el progreso

### Con WebSocket (recomendado)

```js
const { sessionId } = await client.install({ ... });

// Opción 1: usar waitForInstall que lo maneja internamente
const result = await client.waitForInstall(sessionId, (snap) => {
  const pct = snap.overallPercent;
  const done = snap.completedFiles + snap.skippedFiles;
  console.log(`${pct}% — ${done}/${snap.totalFiles} archivos`);
});

// Opción 2: escuchar los eventos manualmente
client.on('session_progress', (data) => {
  if (data.session !== sessionId) return;
  console.log(`${data.percent}%`);
});

client.once('session_completed', (data) => {
  if (data.session !== sessionId) return;
  console.log('Instalación completa');
});

client.once('session_failed', (data) => {
  if (data.session !== sessionId) return;
  console.error('Falló:', data.reason);
});
```

### Con polling (sin WebSocket)

```js
const { sessionId } = await client.install({ ... });

while (true) {
  const snap = await client.progress(sessionId);
  console.log(`${snap.overallPercent}% — estado: ${snap.status}`);

  if (snap.status === 'completed') { console.log('Listo'); break; }
  if (snap.status === 'failed')    { console.error('Error:', snap.error); break; }

  await new Promise(r => setTimeout(r, 1000));
}
```

---

## Lo que instala cada categoría

### `client`
El archivo JAR principal de Minecraft (`1.21.1.jar`). Se guarda en:
```
{instancePath}/versions/{version}/{version}.jar
```

### `libraries`
Todas las librerías Java que necesita Minecraft (LWJGL, Netty, Guava, etc.). Si usás `sharedPath`, van al shared. Si no, van en:
```
{instancePath}/libraries/
```

### `assets`
Los assets del juego (sonidos, texturas del menú, splash texts, etc.). Son cientos de archivos identificados por hash SHA-1. Si usás `sharedPath`, van al shared. El asset index (el JSON que los lista) va siempre en:
```
{instancePath}/assets/indexes/{assetIndex}.json
```

### `natives`
Las librerías nativas de la plataforma (archivos `.so`, `.dll`, `.dylib` para LWJGL y similares). Van en:
```
{instancePath}/versions/{version}/natives/
```

### `jvm`
El runtime de Java que Mojang provee para esa versión. Va en:
```
{instancePath}/runtime/{component}/
```
Donde `component` es algo como `java-runtime-gamma` o `java-runtime-delta`.

---

## Descarga concurrente

El engine usa un thread pool con un límite configurable de threads. Si `maxThreads` es `0`, detecta automáticamente cuántos threads tiene el CPU y usa el doble de núcleos físicos como límite de descarga (con un máximo razonable).

Internamente, cada archivo a descargar se encola como una `DownloadTask`. El `DownloadManager` despacha tasks en paralelo hasta el límite de threads. Si una descarga falla, reintenta hasta 3 veces con backoff antes de marcarla como fallida.

---

## Verificación SHA-1

Con `verifySHA1: true` (el default), antes de descargar cada archivo el engine verifica si ya existe localmente con el hash correcto. Si el hash coincide, el archivo se marca como `skipped` y no se vuelve a bajar.

Esto tiene dos beneficios:
1. Si ya instalaste la versión, una segunda instalación es casi instantánea.
2. Si una descarga anterior quedó corrupta, el engine la detecta y la vuelve a bajar.

Con `verifySHA1: false`, el engine solo verifica si el archivo existe (sin hashear). Es más rápido pero menos confiable.

---

## Instalación con shared path

```js
// Primera instancia: descarga todo (~300-500 MB dependiendo de la versión)
const { sessionId: s1 } = await client.install({
  version:      '1.21.1',
  instancePath: './instances/instancia-a',
  sharedPath:   './shared',
  download:     { client: true, libraries: true, assets: true, natives: true, jvm: true },
});
await client.waitForInstall(s1);

// Segunda instancia: casi todo ya está en ./shared, se reutiliza
const { sessionId: s2 } = await client.install({
  version:      '1.21.1',
  instancePath: './instances/instancia-b',
  sharedPath:   './shared',       // mismo shared
  download:     { client: true, libraries: true, assets: true, natives: true },
});
const result = await client.waitForInstall(s2);
// result.skippedFiles >> result.completedFiles (reutilizó casi todo)
```

---

## Reinstalar / reparar

Si querés forzar que se vuelva a descargar todo aunque ya exista:

```js
await client.install({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  verifySHA1:   false, // no verifica, descarga siempre
});
```

O podés borrar el directorio manualmente y hacer una instalación limpia.

---

## Debuggear una instalación

Si algo falla, podés consultar el estado detallado por categoría:

```js
// Ver todos los archivos de assets de una sesión
const debug = await client.debugCategory('assets', sessionId);

console.log(`Total: ${debug.total}`);
console.log(`Done: ${debug.summary.done}, Skipped: ${debug.summary.skipped}, Failed: ${debug.summary.failed}`);

// Ver los que fallaron
const fallidos = debug.files.filter(f => f.status === 'failed');
fallidos.forEach(f => console.log(`FAILED: ${f.file} → ${f.error}`));
```

O activar el modo debug en la instalación para recibir más eventos:

```js
await client.install({
  version:  '1.21.1',
  instancePath: '...',
  debug: true,  // emite eventos 'debug' con detalles internos
});

client.on('debug', (data) => {
  console.log('[debug]', data.message);
});
```

---

## Eventos del flujo completo de instalación

En orden, estos son los eventos que recibís durante una instalación típica:

```
install_step    → resolving_version
manifest_resolved
tasks_ready     → { totalTasks: 312, breakdown: { client: 1, libraries: 48, assets: 280, natives: 3 } }
session_started → { totalFiles: 312, totalBytes: ~200MB }

(si download.jvm: true)
runtime_download_start
runtime_download_complete

session_progress  (se repite múltiples veces)
session_progress
session_progress
...

install_step    → extracting_natives
session_completed
```
