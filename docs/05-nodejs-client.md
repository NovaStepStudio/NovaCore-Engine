# Cliente Node.js

El cliente Node.js es la forma más rápida de integrar NovaCore-Engine en tu proyecto. Está en `client/` y se compone de dos clases: `CoreProcess` y `CoreClient`.

No necesitás publicarlo en npm para usarlo — podés copiarlo directamente a tu proyecto o referenciarlo con una ruta relativa.

---

## Instalación

```bash
cd client
npm install
```

La única dependencia externa es `ws` (WebSocket client). El resto usa módulos nativos de Node.js.

### Usar en tu propio proyecto

Si querés integrarlo en tu launcher directamente, copiá la carpeta `client/src/` a tu proyecto y hacé:

```bash
npm install ws
```

Después importás así:

```js
const CoreProcess = require('./ruta/a/CoreProcess');
const CoreClient  = require('./ruta/a/CoreClient');
```

Si usás TypeScript, los tipos están en `client/minecraft-core.d.ts`. Agregalo a tu `tsconfig.json` o importalo directamente:

```ts
import type { InstallOptions, LaunchOptions, CoreEvents } from './minecraft-core';
```

---

## CoreProcess

`CoreProcess` se encarga de spawnear el JAR Java y de detectar cuando el engine está listo.

### Constructor

```js
const proc = new CoreProcess({
  jarPath:      '/ruta/a/novacore-engine.jar', // default: ../../build/libs/novacore-engine.jar
  javaPath:     'java',                         // default: 'java' (usa el del sistema)
  httpPort:     7878,                           // default: 7878
  wsPort:       7879,                           // default: 7879
  threads:      32,                             // default: 32 (0 = auto)
  jvmArgs:      ['-Xms32m', '-Xmx128m', '-XX:+UseG1GC'], // default: estos mismos
  verbose:      false,                          // default: false (si true, imprime todo el stdout del engine)
  instancesDir: '/ruta/a/instancias',           // default: null (usa ./instances)
  logDir:       '/ruta/a/logs',                 // default: null (usa ../logs)
  launcherName: 'MiLauncher',                   // default: null
  logLevel:     'INFO',                         // default: null ('DEBUG' | 'INFO' | 'WARN' | 'ERROR')
});
```

### Métodos

**`proc.start()`** → `Promise<void>`

Spawnea el proceso Java y espera a que imprima `Ready`. Si no arranca en 20 segundos, rechaza la promesa.

```js
await proc.start();
console.log('Engine listo. PID:', proc.pid);
```

**`proc.stop()`** → `Promise<void>`

Manda `SIGTERM` al proceso. Si no cierra en 3 segundos, manda `SIGKILL`.

```js
await proc.stop();
```

### Propiedades

```js
proc.running // boolean — true si el proceso está vivo
proc.pid     // number | undefined — PID del proceso Java
```

### Eventos

```js
proc.on('log',    (line) => console.log('[Core]', line));   // líneas de stdout
proc.on('stderr', (line) => console.error('[Java]', line)); // líneas de stderr
proc.on('ready',  ()     => console.log('Engine listo'));   // cuando imprime "Ready"
proc.on('exit',   (code) => console.log('Engine cerró con código', code));
```

### Ejemplo completo

```js
const CoreProcess = require('./CoreProcess');

const proc = new CoreProcess({
  jarPath:      './build/novacore-engine.jar',
  instancesDir: './instances',
  logDir:       './logs',
  launcherName: 'MiLauncher',
  threads:      0, // auto
  verbose:      false,
});

// Manejar cierre limpio con Ctrl+C
process.on('SIGINT', async () => {
  await proc.stop();
  process.exit(0);
});

await proc.start();
console.log('Engine corriendo en PID', proc.pid);
```

---

## CoreClient

`CoreClient` se conecta al engine por HTTP y WebSocket y expone todos los endpoints como métodos async.

Hereda de `EventEmitter`, así que podés escuchar eventos del WebSocket directamente en el cliente.

### Constructor

```js
const client = new CoreClient({
  host:     'localhost', // default: 'localhost'
  httpPort: 7878,        // default: 7878
  wsPort:   7879,        // default: 7879
});
```

### Conexión

**`client.connect()`** → `Promise<void>`

Abre la conexión WebSocket y espera el evento `connected`. Si la conexión ya está abierta, resuelve inmediatamente.

```js
await client.connect();
```

**`client.disconnect()`**

Cierra el WebSocket.

```js
client.disconnect();
```

---

### Métodos de consulta

**`client.apiInfo()`** → `Promise<ApiInfoResponse>`

```js
const info = await client.apiInfo();
console.log(info.version, info.os);
```

**`client.systemResources()`** → `Promise<SystemResourcesResponse>`

```js
const res = await client.systemResources();
console.log(`RAM recomendada: ${res.recommended.mcMaxRamMb} MB`);
console.log(`GC recomendado: ${res.recommended.gcPreset}`);
```

**`client.versions(type?)`** → `Promise<VersionsResponse>`

```js
const releases  = await client.versions('release');
const snapshots = await client.versions('snapshot');
const todas     = await client.versions(); // sin filtro

releases.versions.forEach(v => console.log(v.id, v.releaseTime));
```

---

### Instalación

**`client.install(opts)`** → `Promise<InstallResponse>`

```js
const { sessionId } = await client.install({
  version:      '1.21.1',
  instancePath: '/home/user/.launcher/instances/mi-instancia',
  sharedPath:   '/home/user/.launcher/shared',   // opcional, para compartir assets/libs
  download: {
    client:    true,
    libraries: true,
    assets:    true,
    natives:   true,
    jvm:       false,  // true si querés bajar el JVM de Mojang
  },
  verifySHA1: true,
  maxThreads: 0,       // 0 = auto según CPU
});

console.log('Sesión iniciada:', sessionId);
```

**`client.waitForInstall(sessionId, onProgress?)`** → `Promise<SessionSnapshot>`

Espera a que la instalación termine. Si el WebSocket está conectado usa eventos; si no, hace polling cada 600ms.

```js
const result = await client.waitForInstall(sessionId, (snap) => {
  process.stdout.write(`\r${snap.overallPercent}% — ${snap.completedFiles + snap.skippedFiles}/${snap.totalFiles}`);
});

if (result.status === 'completed') {
  console.log(`\nInstalado. Nuevos: ${result.completedFiles}, Reutilizados: ${result.skippedFiles}`);
} else {
  console.error('Falló:', result.error);
}
```

**`client.progress(sessionId)`** → `Promise<SessionSnapshot>`

Consulta el estado de una sesión en un momento puntual.

**`client.allSessions()`** → `Promise<{ count: number; sessions: SessionSnapshot[] }>`

---

### Lanzamiento

**`client.launch(opts)`** → `Promise<LaunchResponse>`

```js
const { launchId } = await client.launch({
  version:      '1.21.1',
  instancePath: '/home/user/.launcher/instances/mi-instancia',
  sharedPath:   '/home/user/.launcher/shared',
  auth: {
    username:    'MiUsuario',
    uuid:        'uuid-del-jugador',
    accessToken: 'token-microsoft',
    userType:    'msa',
  },
  jvm: {
    minMemoryMb: 512,
    maxMemoryMb: 4096,
  },
  gcPreset: 'g1gc_optimized',
  window: {
    width:  1280,
    height: 720,
  },
});

console.log('Lanzando con ID:', launchId);
```

**`client.waitForGame(launchId)`** → `Promise<{ launchId, exitCode, status }>`

Espera a que el juego cierre.

```js
const result = await client.waitForGame(launchId);
console.log(`Juego cerrado. Status: ${result.status}, Exit code: ${result.exitCode}`);
```

**`client.killLaunch(launchId)`** → `Promise<{ launchId, status }>`

**`client.launchStatus(launchId)`** → `Promise<{ launchId, running, status }>`

---

### Escuchar logs del juego

**`client.onGameLog(launchId, handler)`** → función de cleanup

```js
const unsub = client.onGameLog(launchId, (line) => {
  console.log('[MC]', line);
});

// Para dejar de escuchar:
unsub();
```

---

### Instancias

**`client.createInstance(opts)`** → `Promise<CreateInstanceResponse>`

```js
const inst = await client.createInstance({
  name:      '1.21.1-vanilla',
  mcVersion: '1.21.1',
  config: {
    modLoader:    'vanilla',
    minMemoryMb:  512,
    maxMemoryMb:  4096,
    gcPreset:     'g1gc_optimized',
    launcherName: 'MiLauncher',
  },
  autoInstall: false,
});

console.log('Instancia creada:', inst.id, inst.path);
```

**`client.listInstances()`** → `Promise<{ count, instances }>`

**`client.getInstance(idOrName)`** → `Promise<InstanceInfo>`

**`client.updateInstance(idOrName, updates)`** → `Promise<{ updated, id }>`

```js
await client.updateInstance('1.21.1-vanilla', {
  maxMemoryMb: 8192,
  gcPreset:    'zgc',
});
```

**`client.deleteInstance(idOrName)`** → `Promise<{ deleted, id }>`

---

### Eventos

**`client.onEvent(eventType, handler)`** → función de cleanup

La forma recomendada de escuchar eventos. Devuelve una función para dejar de escuchar.

```js
const unsub = client.onEvent('session_progress', (data) => {
  console.log(`Progreso: ${data.percent}%`);
});

// Cuando ya no necesitás escuchar:
unsub();
```

También podés usar `.on()` y `.off()` directamente, que es la API estándar de `EventEmitter`:

```js
const handler = (data) => console.log(data);
client.on('game_log', handler);
client.off('game_log', handler);
```

Y `.once()` para escuchar solo una vez:

```js
client.once('tasks_ready', (data) => {
  console.log(`Total de tareas: ${data.totalTasks}`);
});
```

**`client.on('*', (eventName, data))`**

Escucha todos los eventos en un solo handler:

```js
client.on('*', (eventName, data) => {
  console.log(`[WS] ${eventName}`, data);
});
```

---

### Runtime de Java

**`client.downloadRuntime(version, instancePath)`**

```js
await client.downloadRuntime('1.21.1', '/ruta/a/instancia');
```

---

## Flujo completo típico

```js
const CoreProcess = require('./CoreProcess');
const CoreClient  = require('./CoreClient');

async function arrancar() {
  // 1. Arrancar el engine
  const proc = new CoreProcess({
    jarPath:      './novacore-engine.jar',
    instancesDir: './instances',
    logDir:       './logs',
    launcherName: 'MiLauncher',
  });

  process.on('SIGINT', async () => { client.disconnect(); await proc.stop(); process.exit(0); });

  await proc.start();

  // 2. Conectar el cliente
  const client = new CoreClient();
  await client.connect();

  // 3. Instalar
  const { sessionId } = await client.install({
    version:      '1.21.1',
    instancePath: './instances/mi-instancia',
    download:     { client: true, libraries: true, assets: true, natives: true },
  });

  await client.waitForInstall(sessionId, (snap) => {
    process.stdout.write(`\r${snap.overallPercent}%`);
  });
  console.log('\nInstalación completa');

  // 4. Lanzar
  const { launchId } = await client.launch({
    version:      '1.21.1',
    instancePath: './instances/mi-instancia',
    auth:         { username: 'Jugador', userType: 'offline' },
  });

  client.onGameLog(launchId, (line) => console.log('[MC]', line));
  const exit = await client.waitForGame(launchId);
  console.log('Juego cerrado:', exit.status);

  // 5. Limpiar
  client.disconnect();
  await proc.stop();
}

arrancar().catch(console.error);
```
