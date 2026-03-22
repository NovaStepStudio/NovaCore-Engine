# Lanzamiento de Minecraft

El sistema de lanzamiento construye y ejecuta el proceso Java de Minecraft con todos los argumentos correctos. Soporta autenticación MSA, modo offline, authlib-injector, configuración de JVM, y más.

---

## Lanzamiento básico (offline)

```js
const { launchId } = await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  auth: {
    username: 'Jugador',
    userType: 'offline',
  },
});

client.onGameLog(launchId, (line) => console.log('[MC]', line));
const result = await client.waitForGame(launchId);
console.log('Cerrado con status:', result.status); // 'clean' o 'crash'
```

---

## Autenticación

### MSA (Microsoft)

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  auth: {
    username:    'MiUsuario',
    uuid:        'uuid-del-perfil-de-mojang',
    accessToken: 'token-de-microsoft',
    userType:    'msa',
    clientId:    'client-id-del-token',
    xuid:        'xuid-de-xbox-live',
  },
});
```

### Legacy (Mojang)

```js
await client.launch({
  version:      '1.12.2',
  instancePath: './instances/mi-instancia',
  auth: {
    username:    'MiUsuario',
    uuid:        'uuid',
    accessToken: 'token-legacy',
    userType:    'legacy',
  },
});
```

### Authlib-Injector (servidores privados)

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  auth: {
    username:    'MiUsuario',
    uuid:        'uuid',
    accessToken: 'token-del-servidor',
    userType:    'msa',
  },
  authlibInjector: {
    enabled:   true,
    jarPath:   './authlib-injector.jar',
    serverUrl: 'https://auth.mi-servidor.com',
  },
});
```

---

## Configuración de JVM

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  jvm: {
    minMemoryMb: 512,         // -Xms512m (0 = sin mínimo explícito)
    maxMemoryMb: 4096,        // -Xmx4096m (0 = auto según systemResources)
    extraArgs:   [            // se agregan al final de los args JVM
      '-XX:+UnlockExperimentalVMOptions',
      '-Dfml.ignoreInvalidMinecraftCertificates=true',
    ],
    prependArgs: [            // se ponen antes de los args JVM resueltos
      '-javaagent:/ruta/a/agente.jar',
    ],
  },
});
```

### Presets de GC

En vez de configurar el GC a mano podés usar los presets:

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  gcPreset:     'g1gc_optimized', // 'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah'
});
```

| Preset | Para quién |
|---|---|
| `auto` | Deja que el engine elija según RAM del sistema |
| `g1gc_basic` | Máquinas con 4-8 GB de RAM |
| `g1gc_optimized` | Máquinas con 8-16 GB, el mejor balance para Minecraft |
| `zgc` | Máquinas con 16+ GB, latencia mínima (Java 17+) |
| `shenandoah` | Alternativa open-source a ZGC |

---

## Configurar la ventana del juego

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  window: {
    width:      1920,
    height:     1080,
    fullscreen: false,
  },
});
```

---

## Java personalizado

Por default el engine usa el JVM bundled de Mojang (si fue descargado) o el Java del sistema. Si querés usar uno específico:

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  javaPath:     '/usr/lib/jvm/java-21-openjdk/bin/java',
});
```

---

## Preferencia de GPU

En laptops o sistemas con GPU integrada + dedicada, podés controlar cuál usa Minecraft:

```js
await client.launch({
  version:        '1.21.1',
  instancePath:   './instances/mi-instancia',
  gpuPreference:  'dgpu', // 'auto' | 'dgpu' | 'igpu'
});
```

Esto agrega los argumentos de sistema correspondientes para que Minecraft use la GPU dedicada en Windows y Linux.

---

## Aceleración de hardware

```js
await client.launch({
  version:              '1.21.1',
  instancePath:         './instances/mi-instancia',
  hardwareAcceleration: true,
});
```

---

## Branding del launcher

Podés pasar el nombre y versión de tu launcher para que aparezcan en el menú principal de Minecraft:

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  launcher: {
    name:    'StepLauncher',
    version: '2.0.0',
  },
});
```

---

## Personalización del juego

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  game: {
    gameDir:             './instancias/mi-mundo',  // directorio custom para saves, options.txt, etc.
    extraGameArgs:       ['--demo'],               // args adicionales para el juego
    extraJvmProperties:  {
      'java.awt.headless': 'true',
    },
    disableMultiplayer:  false,                    // deshabilita el botón de multijugador
    disableChat:         false,                    // deshabilita el chat
    serverHost:          'play.mi-servidor.com',   // conecta directo a un servidor al arrancar
    serverPort:          25565,
  },
});
```

---

## Quick Play (Minecraft 1.20+)

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  features: {
    quickPlay: {
      mode:  'multiplayer',        // 'singleplayer' | 'multiplayer' | 'realms'
      value: 'play.hypixel.net',   // dirección del servidor para multiplayer
    },
  },
});
```

---

## Escuchar los logs del juego

```js
const { launchId } = await client.launch({ ... });

// Opción simple
const unsub = client.onGameLog(launchId, (line) => {
  if (line.includes('ERROR') || line.includes('FATAL')) {
    console.error('[MC ERROR]', line);
  } else {
    console.log('[MC]', line);
  }
});

// Cuando Minecraft cierre, dejar de escuchar
const result = await client.waitForGame(launchId);
unsub();
console.log(`Minecraft cerró: ${result.status} (código ${result.exitCode})`);
```

---

## Matar un proceso en ejecución

```js
const { launchId } = await client.launch({ ... });

// En algún momento después...
await client.killLaunch(launchId);
```

---

## Verificar si el juego sigue corriendo

```js
const status = await client.launchStatus(launchId);
if (status.running) {
  console.log('Minecraft está corriendo');
} else {
  console.log('Minecraft cerró');
}
```

---

## Lanzamiento completo con todos los eventos

```js
const { launchId } = await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  auth: {
    username:    'MiUsuario',
    uuid:        'uuid',
    accessToken: 'token',
    userType:    'msa',
  },
  jvm:         { minMemoryMb: 512, maxMemoryMb: 4096 },
  gcPreset:    'g1gc_optimized',
  window:      { width: 1280, height: 720 },
  launcher:    { name: 'StepLauncher', version: '2.0.0' },
});

client.once('launch_preparing',     (d) => console.log('Preparando...', d.version));
client.once('launch_command_ready', (d) => console.log('Comando listo. Main class:', d.mainClass));
client.once('launch_started',       (d) => console.log('Minecraft iniciado para', d.username));
client.once('launch_failed',        (d) => console.error('Error al lanzar:', d.error));

const unsub = client.onGameLog(launchId, (line) => process.stdout.write(line + '\n'));

const result = await client.waitForGame(launchId);
unsub();

if (result.status === 'crash') {
  console.error('Minecraft crasheó con código', result.exitCode);
} else {
  console.log('Minecraft cerró normalmente');
}
```

---

## Shared path en el lanzamiento

Si instalaste con `sharedPath`, tenés que pasarlo también en el lanzamiento para que el engine sepa dónde buscar las librerías:

```js
await client.launch({
  version:      '1.21.1',
  instancePath: './instances/mi-instancia',
  sharedPath:   './shared',  // mismo que usaste en install()
  auth: { ... },
});
```
