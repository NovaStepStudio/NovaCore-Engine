# Lanzamiento de Minecraft

El sistema de lanzamiento construye el comando Java completo — classpath, argumentos JVM, argumentos del juego — y levanta el proceso. Soporta versiones vanilla y todos los modloaders, autenticación MSA y offline, authlib-injector, y configuración fina de JVM.

Antes de lanzar, el engine verifica que todos los componentes estén presentes (client.jar, nativos, librerías, java). Si algo falta, devuelve el evento `launch_verification_failed` con la lista de componentes faltantes y un hint para repararlos.

---

## Lanzamiento básico (offline)

```js
const { launchId } = await client.launch({
  version:      '1.21.6',
  instancePath: './instances/mi-instancia',
  auth: {
    username: 'Jugador',
    userType: 'offline',
  },
});

client.onGameLog(launchId, (line) => console.log('[MC]', line));

const result = await client.waitForGame(launchId);
console.log('Salió con código:', result.exitCode);
```

---

## Lanzamiento con cuenta MSA

```js
const { launchId } = await client.launch({
  version:      '1.21.6',
  instancePath: './instances/mi-instancia',
  auth: {
    username:    'MiUsuario',
    uuid:        'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx',
    accessToken: 'eyJ...',
    userType:    'msa',
    clientId:    'mi-client-id',
    xuid:        'mi-xuid',
  },
});
```

---

## Lanzamiento con modloader

No hay nada especial que hacer en el launch para usar un modloader. El engine detecta el prefijo del `version` (`fabric-`, `forge-`, `quilt-`, etc.) y ajusta automáticamente el main class y el classpath.

```js
const { launchId } = await client.launch({
  version:      'fabric-1.21.6-0.18.6',
  instancePath: './instances/mi-instancia',
  auth: { username: 'Jugador', userType: 'offline' },
});
```

```js
const { launchId } = await client.launch({
  version:      '1.21.6-forge-56.0.9',
  instancePath: './instances/mi-instancia',
  auth: { username: 'Jugador', userType: 'offline' },
});
```

---

## Configuración de JVM

```js
const { launchId } = await client.launch({
  version:      '1.21.6',
  instancePath: './instances/mi-instancia',
  auth: { username: 'Jugador', userType: 'offline' },
  jvm: {
    minMemoryMb: 512,
    maxMemoryMb: 4096,
    extraArgs:   ['-XX:+UseStringDeduplication'],
    prependArgs: ['-Dfile.encoding=UTF-8'],
  },
  gcPreset:            'g1gc_optimized',
  hardwareAcceleration: true,
  gpuPreference:        'dgpu',
});
```

### GC Presets

| Preset            | Descripción                                                   |
|-------------------|---------------------------------------------------------------|
| `auto`            | Deja que la JVM elija (default)                               |
| `g1gc_basic`      | G1GC con parámetros conservadores                             |
| `g1gc_optimized`  | G1GC ajustado para gaming, reduce pauses                      |
| `zgc`             | ZGC, muy bajas latencias, necesita Java 15+                   |
| `shenandoah`      | Shenandoah GC, similar a ZGC                                  |

---

## Resolución de Java

El engine resuelve el ejecutable de Java en este orden:

1. Si `javaPath` está especificado en el request (y no es el literal `"java"`), se usa ese.
2. Busca en `sharedPath/runtime/` una carpeta `java-*` con el ejecutable dentro.
3. Busca en `instancePath/runtime/` lo mismo.
4. Usa el Java de `java.home` del propio engine.
5. Fallback a `"java"` del PATH del sistema.

Si instalaste el JVM con `download.jvm: true`, el engine lo va a encontrar automáticamente en el paso 2 o 3. No necesitás especificar `javaPath` para nada.

---

## Seguir el progreso

```js
client.on('launch_preparing',  (d) => console.log('Preparando...', d.version));
client.on('launch_starting',   (d) => console.log('Iniciando', d.mainClass));
client.on('launch_started',    (d) => console.log('PID:', d.pid));

client.on('game_log', (d) => {
  if (d.launchId !== launchId) return;
  const prefix = d.stream === 'stderr' ? '[ERR]' : '[OUT]';
  console.log(prefix, d.line);
});

client.on('launch_exited', (d) => {
  if (d.launchId !== launchId) return;
  console.log('Salió con código:', d.exitCode);
});

client.on('launch_failed', (d) => {
  if (d.launchId !== launchId) return;
  console.error('Falló:', d.error);
});
```

Si la verificación pre-launch detecta componentes faltantes:

```js
client.on('launch_verification_failed', (d) => {
  if (d.launchId !== launchId) return;
  console.error('Faltan componentes:', d.missing);
  console.log('Hint:', d.hint); // normalmente "Re-run install to repair..."
});
```

---

## Opciones completas

```ts
interface LaunchOptions {
  version:               string;
  instancePath:          string;
  sharedPath?:           string;
  javaPath?:             string;
  hardwareAcceleration?: boolean;
  gcPreset?:             'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
  gpuPreference?:        'auto' | 'dgpu' | 'igpu';
  auth?: {
    username?:    string;
    uuid?:        string;
    accessToken?: string;
    userType?:    'msa' | 'legacy' | 'offline';
    clientId?:    string;
    xuid?:        string;
  };
  authlibInjector?: {
    enabled:   boolean;
    jarPath:   string;
    serverUrl: string;
  };
  jvm?: {
    minMemoryMb?:  number;
    maxMemoryMb?:  number;
    extraArgs?:    string[];
    prependArgs?:  string[];
  };
  window?: {
    width?:      number;
    height?:     number;
    fullscreen?: boolean;
  };
  launcher?: {
    name?:    string;
    version?: string;
  };
  features?: {
    demo?: boolean;
    quickPlay?: { mode: 'singleplayer' | 'multiplayer' | 'realms'; value: string };
  };
  game?: {
    gameDir?:            string;
    extraGameArgs?:      string[];
    extraJvmProperties?: Record<string, string>;
    serverHost?:         string;
    serverPort?:         number;
  };
}
```

---

## Matar el juego

```js
const { killed } = await client.killLaunch(launchId);
```

---

## Authlib-Injector

Para launchers con autenticación custom (Ely.by, etc.):

```js
const { launchId } = await client.launch({
  version:      '1.21.6',
  instancePath: './instances/mi-instancia',
  auth: {
    username:    'MiUsuario',
    accessToken: 'token-de-mi-auth-server',
    userType:    'msa',
  },
  authlibInjector: {
    enabled:   true,
    jarPath:   './authlib-injector.jar',
    serverUrl: 'https://mi-auth-server.com/api/yggdrasil',
  },
});
```
