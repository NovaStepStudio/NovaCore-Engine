<div align="center">
  <img src="docs/NovaCore-Engine.png" alt="NovaCore-Engine" width="500"/>
  <h1>NovaCore-Engine</h1>
  <p><strong>El motor de backend definitivo para launchers de Minecraft Java</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Java-25-orange?style=flat-square"/>
    <img src="https://img.shields.io/badge/Node.js-18+-green?style=flat-square"/>
    <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-blue?style=flat-square"/>
    <img src="https://img.shields.io/badge/License-Apache%202.0-purple?style=flat-square"/>
  </p>
</div>

---

NovaCore-Engine es un motor de alto rendimiento que potencia launchers modernos. Es un proceso Java independiente que centraliza toda la lógica compleja de Minecraft —instalación, gestión de activos, resolución de dependencias y lanzamiento— exponiéndola a través de una API HTTP y eventos WebSocket en tiempo real.

---

## Arquitectura

El motor funciona como un servidor local que tu aplicación controla.

- **API HTTP** (:7878) — Comandos directos: instalar, lanzar, gestionar instancias, consultar versiones.
- **WebSocket** (:7879) — Eventos: progreso granular de descargas, logs del juego, cambios de estado.
- **GET /health** — Endpoint de health-check para monitorear si el engine sigue vivo.

```text
Tu App / Launcher (UI)
        │
        ├── [HTTP] ──► Control de operaciones (POST /install, POST /launch, GET /health)
        └── [WS]   ──◄ Feedback en tiempo real (Eventos de progreso, Logs)
              │
        NovaCore-Engine (Proceso Java independiente)
              │
        Mojang APIs + Sistema de archivos local + Minecraft Runtime
```

---

## Características Principales

- **Rendimiento con Virtual Threads**: Basado en Java 25 (Project Loom) para manejar miles de conexiones.
- **ModLoaders Integrados**: Soporte nativo para Forge, Fabric, Quilt, NeoForge, LegacyFabric y OptiFine.
- **Health-Check Automático**: El cliente monitorea el engine periódicamente y emite eventos si se cae.
- **Auto-Cleanup**: El proceso Java se cierra automáticamente cuando el proceso Node termina.
- **Cierre Limpio (Tree Kill)**: Gestión inteligente de procesos que asegura que Minecraft y sus subprocesos se cierren correctamente.
- **Caché Compartida**: Sistema de `sharedPath` para bibliotecas y activos, ahorrando gigabytes de espacio en disco.
- **PID File**: El engine escribe `engine.pid` en el directorio de logs para monitoreo externo.

---

## Requisitos del Sistema

| Componente  | Versión | Propósito                                             |
| :---------- | :------ | :---------------------------------------------------- |
| **Java**    | 25      | Ejecución del motor principal (Core).                 |
| **Node.js** | 18+     | Uso del cliente oficial y herramientas de desarrollo. |
| **Gradle**  | 9.4+    | Compilación del motor (opcional).                     |

---

## Inicio Rápido

### 1. Compilar el Engine

```bash
cd core
./gradlew build
```

El ejecutable se genera en `core/build/libs/novacore-engine.jar`.

### 2. Instalación del Cliente

```bash
npm install @novastepstudios/novacore-engine-client
```

### 3. Ejemplo de Uso

```typescript
import { NovaCoreEngine } from "@novastepstudios/novacore-engine-client";

const client = await NovaCoreEngine.start({ jar: "./novacore-engine.jar" });

client.on("engine_unreachable", (d) => {
  console.error("Engine cayo:", d.reason);
});

await client.install({
  version: "1.21.1",
  instancePath: "./game",
  launcher: { name: "MiLauncher" },
});

client.on("session_progress", (p) => {
  console.log(`Progreso: ${p.overallPercent}%`);
});

await client.launch({
  version: "1.21.1",
  instancePath: "./game",
  jvm: { maxMemoryMb: 4096 },
  gcPreset: "g1gc_optimized",
});
```

El engine se cierra automáticamente cuando el proceso Node termina. No necesitas llamar `client.disconnect()` ni `client.closeEngine()` manualmente.

---

## API del Cliente

### NovaCoreEngine

```typescript
// Inicio simple — retorna un NovaCoreClient listo para usar
const client = await NovaCoreEngine.start({ jar: "./engine.jar" });

// Inicio con control del proceso
const { client, process } = await NovaCoreEngine.startWithHandle({
  jar: "./engine.jar",
});
```

### NovaCoreClient

```typescript
// Eventos del engine (WebSocket)
client.on("install_step", (data) => {});
client.on("session_progress", (data) => {});
client.on("launch_started", (data) => {});
client.on("game_log", (data) => {});

// Evento de health-check (se dispara si el engine no responde)
client.on("engine_unreachable", (data) => {});

// Instalación
await client.install({ version: "1.21.1", instancePath: "./game" });

// Lanzamiento
const handle = await client.launch({ version: "1.21.1", instancePath: "./game", auth: {...} });
await handle.exited; // Espera a que el juego termine

// Shutdown manual (opcional — el auto-cleanup lo hace por ti)
await client.shutdown();
```

### EngineProcess

```typescript
const { process } = await NovaCoreEngine.startWithHandle({
  jar: "./engine.jar",
});

// Health-check manual
const alive = await process.healthCheck();

// Forzar detención
await process.stop();
process.kill();
```

---

## Estructura del Proyecto

```bash
NovaCore-Engine/
├── core
│   ├── build.bat
│   ├── build.gradle
│   ├── build.sh
│   ├── gradle
│   │   └── wrapper
│   │       └── gradle-wrapper.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── settings.gradle
│   ├── src
│   │   └── main
│   │       └── java
│   │           ├── com
│   │           │   └── fasterxml
│   │           │       └── jackson
│   │           │           └── core
│   │           │               └── type
│   │           │                   └── TypeReference.java
│   │           └── dev
│   │               └── novastep
│   │                   └── core
│   │                       ├── CoreVersion.java
│   │                       ├── downloader
│   │                       │   ├── DownloadControl.java
│   │                       │   ├── DownloadManager.java
│   │                       │   ├── DownloadPriority.java
│   │                       │   ├── DownloadSession.java
│   │                       │   ├── FileDownloader.java
│   │                       │   ├── model
│   │                       │   │   ├── DownloadResult.java
│   │                       │   │   └── DownloadTask.java
│   │                       │   └── Sha1Verifier.java
│   │                       ├── json
│   │                       │   ├── JacksonCompatibilityAdapter.java
│   │                       │   ├── Json.java
│   │                       │   └── JsonParserLite.java
│   │                       ├── log
│   │                       │   └── CoreLogger.java
│   │                       ├── Main.java
│   │                       ├── minecraft
│   │                       │   ├── ArgumentResolver.java
│   │                       │   ├── ClasspathBuilder.java
│   │                       │   ├── common
│   │                       │   │   └── ModuleStatus.java
│   │                       │   ├── CrashReporter.java
│   │                       │   ├── diagnostic
│   │                       │   │   ├── ContextAnalyzer.java
│   │                       │   │   └── ContextAnalyzerRunner.java
│   │                       │   ├── GameLogManager.java
│   │                       │   ├── InstallOrchestrator.java
│   │                       │   ├── InstallVerifier.java
│   │                       │   ├── instance
│   │                       │   │   ├── DefaultInstanceConfig.java
│   │                       │   │   ├── InstanceConfigMerger.java
│   │                       │   │   ├── InstanceConfigStore.java
│   │                       │   │   ├── InstanceTechnicalMetadataStore.java
│   │                       │   │   ├── LaunchInstanceConfigResolver.java
│   │                       │   │   └── LegacyInstanceMetadataMigrator.java
│   │                       │   ├── LibraryResolver.java
│   │                       │   ├── manifest
│   │                       │   │   ├── ManifestClient.java
│   │                       │   │   └── VersionMerger.java
│   │                       │   ├── MavenPathResolver.java
│   │                       │   ├── MinecraftLauncher.java
│   │                       │   ├── MinecraftVerifier.java
│   │                       │   ├── ModuleTracker.java
│   │                       │   ├── NativeHandler.java
│   │                       │   ├── RuleEvaluator.java
│   │                       │   ├── RuntimeDownloader.java
│   │                       │   ├── SessionManager.java
│   │                       │   ├── TaskBuilder.java
│   │                       │   ├── version
│   │                       │   │   ├── AssetIndexManifest.java
│   │                       │   │   ├── VersionInfo.java
│   │                       │   │   └── VersionManifest.java
│   │                       │   └── world
│   │                       │       └── WorldModels.java
│   │                       ├── modloader
│   │                       │   ├── installer
│   │                       │   │   ├── InstallerExecutor.java
│   │                       │   │   └── MavenCoordinate.java
│   │                       │   ├── model
│   │                       │   │   ├── InstallProfile.java
│   │                       │   │   └── ModLoaderModels.java
│   │                       │   ├── ModLoaderOrchestrator.java
│   │                       │   ├── ModLoaderProvider.java
│   │                       │   ├── ModLoaderRegistry.java
│   │                       │   ├── provider
│   │                       │   │   ├── AbstractFabricProvider.java
│   │                       │   │   ├── AbstractForgeProvider.java
│   │                       │   │   ├── FabricProvider.java
│   │                       │   │   ├── ForgeProvider.java
│   │                       │   │   ├── LegacyFabricProvider.java
│   │                       │   │   ├── NeoForgeProvider.java
│   │                       │   │   ├── OptiFineProvider.java
│   │                       │   │   └── QuiltProvider.java
│   │                       │   └── resolver
│   │                       │       └── NeoForgeVersionResolver.java
│   │                       ├── server
│   │                       │   ├── CoreHttpServer.java
│   │                       │   ├── handlers
│   │                       │   │   ├── ApiHandler.java
│   │                       │   │   ├── CloseHandler.java
│   │                       │   │   ├── DebugHandler.java
│   │                       │   │   ├── EngineStateHandler.java
│   │                       │   │   ├── HealthHandler.java
│   │                       │   │   ├── InstallHandler.java
│   │                       │   │   ├── LaunchHandler.java
│   │                       │   │   ├── ModLoaderHandler.java
│   │                       │   │   ├── ProgressHandler.java
│   │                       │   │   ├── QuickPlayHandler.java
│   │                       │   │   ├── RuntimeHandler.java
│   │                       │   │   ├── SystemResourcesHandler.java
│   │                       │   │   ├── TelemetryHandler.java
│   │                       │   │   └── VersionsHandler.java
│   │                       │   ├── HttpUtils.java
│   │                       │   └── request
│   │                       │       ├── InstallRequest.java
│   │                       │       ├── LaunchRequest.java
│   │                       │       └── ModLoaderRequest.java
│   │                       ├── state
│   │                       │   └── EngineStateManager.java
│   │                       ├── util
│   │                       │   ├── JavaResolver.java
│   │                       │   ├── MemoryOptimizer.java
│   │                       │   ├── NbtReader.java
│   │                       │   ├── ProcessUtils.java
│   │                       │   └── SystemResources.java
│   │                       └── websocket
│   │                           └── EventBroadcaster.java
│   └── version.txt
├── docs
│   ├── 01-architecture.md
│   ├── 02-building.md
│   ├── 03-http-api.md
│   ├── 04-websocket-events.md
│   ├── 05-nodejs-client.md
│   ├── 06-instances.md
│   ├── 07-install.md
│   ├── 08-launch.md
│   ├── 09-java-integration.md
│   ├── 10-type-reference.md
│   └── NovaCore-Engine.png
├── LICENSE.md
├── @novastepstudios
│   └── novacore-engine-client
│       ├── bun.lock
│       ├── example
│       │   ├── 00-config.js
│       │   ├── 01-install-and-launch-using-instance-config.js
│       │   ├── 02-vanilla-install.js
│       │   ├── 03-modloader-install.js
│       │   ├── 04-basic-launch.js
│       │   ├── 05-event-monitor.js
│       │   ├── 06-process-management.js
│       │   ├── 07-engine-info.js
│       │   ├── 08-runtime-manager.js
│       │   ├── 09-jvm-customization.js
│       │   ├── 10-telemetry-crashes.js
│       │   ├── 11-recovery-system.js
│       │   ├── 12-auth-modes.js
│       │   ├── 13-world-saves.js
│       │   ├── 14-mod-checker.js
│       │   ├── 15-comprehensive-flow.js
│       │   └── README.md
│       ├── package.json
│       ├── README.md
│       ├── src
│       │   ├── EngineProcess.ts
│       │   ├── index.ts
│       │   ├── InstallFlow.ts
│       │   ├── internal
│       │   │   ├── HttpClient.ts
│       │   │   └── WsClient.ts
│       │   ├── JvmArgsHelper.ts
│       │   ├── LaunchFlow.ts
│       │   ├── minecraft
│       │   ├── NovaCoreClient.ts
│       │   ├── NovaCoreEngine.ts
│       │   └── types
│       │       └── index.ts
│       └── tsconfig.json
├── README.md
└── README.old.md
```
