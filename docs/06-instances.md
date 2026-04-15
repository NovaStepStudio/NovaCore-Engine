# Instancias

El sistema de instancias de NovaCore-Engine te permite gestionar múltiples instalaciones de Minecraft de forma independiente, cada una con su propia configuración de memoria, mod loader, argumentos JVM, y más.

Cada instancia se guarda como un archivo `instance.json` dentro de su directorio. No hay base de datos — todo es el filesystem.

---

## ¿Qué es una instancia?

Una instancia es básicamente una carpeta de Minecraft con metadata asociada. Dentro de esa carpeta van los archivos del juego (`versions/`, `libraries/`, `assets/`, `natives/`) y un archivo `instance.json` con la configuración.

```
instances/
└── 1.21.1-vanilla/
    ├── instance.json          ← metadata de la instancia
    ├── versions/
    │   └── 1.21.1/
    │       ├── 1.21.1.jar
    │       └── 1.21.1.json
    ├── libraries/
    ├── assets/                ← si no hay sharedPath, los assets van acá
    └── natives/
```

Si usás `sharedPath`, los assets y librerías van al directorio compartido y las instancias simplemente los referencian. Así podés tener 10 instancias de la misma versión sin duplicar 300 MB de assets.

---

## Crear una instancia

### Vía API (Node.js)

```js
const inst = await client.createInstance({
  name:      '1.21.1-survival',
  mcVersion: '1.21.1',
  config: {
    modLoader:      'vanilla',
    minMemoryMb:    512,
    maxMemoryMb:    4096,
    gcPreset:       'g1gc_optimized',
    launcherName:   'MiLauncher',
    launcherVersion: '1.0.0',
  },
});

console.log('ID:', inst.id);
console.log('Path:', inst.path);
```

### Crear e instalar en el mismo paso

Si pasás `autoInstall: true`, el engine también dispara la instalación automáticamente:

```js
const inst = await client.createInstance({
  name:      '1.21.1-survival',
  mcVersion: '1.21.1',
  config: {
    modLoader:   'vanilla',
    maxMemoryMb: 4096,
  },
  autoInstall: true,
  install: {
    sharedPath: '/home/user/.launcher/shared',
    download: {
      client:    true,
      libraries: true,
      assets:    true,
      natives:   true,
    },
    verifySHA1: true,
  },
});

// Si autoInstall: true, la respuesta incluye installSessionId
if (inst.installSessionId) {
  await client.waitForInstall(inst.installSessionId, (snap) => {
    process.stdout.write(`\r${snap.overallPercent}%`);
  });
  console.log('\nInstancia lista');
}
```

### Vía HTTP directa

```bash
curl -X POST http://localhost:7878/instances \
  -H "Content-Type: application/json" \
  -d '{
    "name": "1.21.1-survival",
    "mcVersion": "1.21.1",
    "config": {
      "modLoader": "vanilla",
      "minMemoryMb": 512,
      "maxMemoryMb": 4096,
      "gcPreset": "g1gc_optimized"
    }
  }'
```

---

## Listar instancias

```js
const { count, instances } = await client.listInstances();

instances.forEach((inst) => {
  console.log(`${inst.name} (${inst.mcVersion}) — ${inst.installed ? '✓ instalada' : '○ sin instalar'}`);
  console.log(`  RAM: ${inst.minMemoryMb}-${inst.maxMemoryMb} MB`);
  console.log(`  GC: ${inst.gcPreset}`);
  console.log(`  Tiempo jugado: ${inst.totalPlayHours}`);
});
```

---

## Obtener una instancia

Podés buscar por ID o por nombre:

```js
const inst = await client.getInstance('1.21.1-survival');
// o
const inst = await client.getInstance('uuid-de-la-instancia');
```

Para obtener solo la ruta al directorio:

```js
const { path } = await client.getInstancePath('1.21.1-survival');
```

---

## Actualizar la configuración

```js
await client.updateInstance('1.21.1-survival', {
  maxMemoryMb: 8192,
  gcPreset:    'zgc',
  jvmArgs:     ['-XX:+UnlockExperimentalVMOptions'],
});
```

Los campos que no mandás no se tocan. Solo se actualizan los que pasás.

---

## Borrar una instancia

```js
await client.deleteInstance('1.21.1-survival');
```

Esto borra el registro de la instancia del `instance.json`, pero **no borra los archivos del disco**. Si querés limpiar el directorio tenés que hacerlo vos manualmente.

---

## Configuración completa de una instancia

Estos son todos los campos que podés configurar en `config` al crear o actualizar:

| Campo | Tipo | Default | Descripción |
|---|---|---|---|
| `modLoader` | `string` | `"vanilla"` | `vanilla`, `fabric`, `forge`, `neoforge`, `quilt`, `liteloader` |
| `modLoaderVersion` | `string` | — | Versión del mod loader |
| `javaPath` | `string` | `null` | Ruta personalizada al ejecutable Java para esta instancia |
| `minMemoryMb` | `number` | `512` | RAM mínima en MB |
| `maxMemoryMb` | `number` | `2048` | RAM máxima en MB |
| `hardwareAccel` | `boolean` | `false` | Activar aceleración de hardware |
| `gcPreset` | `string` | `null` | `auto`, `g1gc_basic`, `g1gc_optimized`, `zgc`, `shenandoah` |
| `jvmArgs` | `string[]` | `[]` | Args JVM adicionales |
| `extraGameArgs` | `string[]` | `[]` | Args adicionales para el juego |
| `jvmProperties` | `Record<string, string>` | `{}` | Propiedades JVM (`-Dproperty=value`) |
| `launcherName` | `string` | `null` | Nombre del launcher (para branding en el menú de Minecraft) |
| `launcherVersion` | `string` | `null` | Versión del launcher |
| `serverHost` | `string` | `null` | Conectar automáticamente a este servidor al lanzar |
| `serverPort` | `number` | `null` | Puerto del servidor |
| `customGameDir` | `string` | `null` | Directorio de juego personalizado (saves, resourcepacks, etc.) |

---

## Presets de GC

El engine incluye presets de recolector de basura listos para usar:

| Preset | Cuándo usar |
|---|---|
| `auto` | Deja que el engine elija según la RAM disponible |
| `g1gc_basic` | Máquinas con poca RAM (< 8 GB). Configuración conservadora de G1GC. |
| `g1gc_optimized` | Máquinas con RAM media/alta. G1GC con parámetros optimizados para Minecraft. |
| `zgc` | Máquinas con mucha RAM (16+ GB). ZGC tiene pausas mínimas. Requiere Java 17+. |
| `shenandoah` | Alternativa a ZGC. Disponible en OpenJDK builds, no en Oracle JDK. |

Para la mayoría de los casos `g1gc_optimized` es el mejor balance. Si el usuario tiene 32 GB de RAM y una máquina potente, `zgc` puede dar una experiencia más fluida.

---

## SharedPath: compartir archivos entre instancias

Si tenés varias instancias de la misma versión (o versiones que comparten assets), podés usar un `sharedPath` para evitar duplicar cientos de MB en disco.

```
shared/
├── assets/           ← compartido entre todas las instancias
├── libraries/        ← compartido entre instancias de la misma versión
└── runtimes/         ← JVM de Mojang, compartido

instances/
├── 1.21.1-vanilla/
│   ├── instance.json
│   ├── versions/1.21.1/1.21.1.jar   ← el client.jar va en la instancia
│   └── natives/                      ← los nativos también
└── 1.21.1-creative/
    ├── instance.json
    └── versions/1.21.1/1.21.1.jar
```

El engine verifica SHA-1 antes de descargar. Si un archivo ya está en el shared path con el hash correcto, lo marca como `skipped` y no lo vuelve a bajar. Esto hace que la segunda, tercera, y cuarta instancia de la misma versión "se instalen" en segundos.

```js
// Primera instancia: descarga todo
await client.install({
  version:      '1.21.1',
  instancePath: './instances/vanilla',
  sharedPath:   './shared',
});

// Segunda instancia: reutiliza casi todo del shared
await client.install({
  version:      '1.21.1',
  instancePath: './instances/creative',
  sharedPath:   './shared',  // mismo shared path
});
// → completedFiles: 1 (solo el client.jar), skippedFiles: 311
```
