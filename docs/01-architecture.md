# Arquitectura de NovaCore-Engine

NovaCore-Engine está dividido en dos componentes que trabajan juntos: el **engine Java** (el backend pesado) y el **cliente Node.js** (la interfaz de tu app). Acá te explico cómo está organizado todo por dentro.

---

## Visión general

El engine es un proceso Java independiente. Cuando arrancás tu launcher, él spawnea el JAR, espera que esté listo, y a partir de ahí toda la comunicación es HTTP y WebSocket.

```
┌─────────────────────────────────────────────────────────┐
│  Tu aplicación (Node.js / Electron / Java / lo que sea) │
│                                                         │
│   CoreProcess  ──spawn──►  novacore-engine.jar          │
│   CoreClient   ◄──HTTP──►  :7878 (REST API)             │
│   CoreClient   ◄──WS───►  :7879 (WebSocket events)      │
└─────────────────────────────────────────────────────────┘
```

Cuando el JAR imprime `Ready` en stdout, el engine está listo para recibir requests. El `CoreProcess` detecta esa línea y resuelve la promesa de `start()`.

---

## Estructura interna del engine Java

```
dev.novastep.core/
│
├── Main.java                    # Entry point. Parsea args, inicializa todo.
│
├── server/                      # Capa HTTP
│   ├── CoreHttpServer.java      # Levanta el HttpServer de Java, registra rutas
│   ├── HttpUtils.java           # Helpers para escribir respuestas JSON
│   ├── InstallRequest.java      # POJO para el body de /install
│   ├── LaunchRequest.java       # POJO para el body de /launch
│   └── handlers/                # Un handler por endpoint
│       ├── ApiHandler.java      # GET /api → info de la API
│       ├── InstallHandler.java  # POST /install
│       ├── LaunchHandler.java   # POST /launch, /launch/kill, /launch/status
│       ├── InstanceHandler.java # CRUD de instancias
│       ├── VersionsHandler.java # GET /versions
│       ├── RuntimeHandler.java  # POST /runtime/download
│       ├── ProgressHandler.java # GET /progress
│       ├── SystemResourcesHandler.java  # GET /system/resources
│       └── DebugHandler.java    # GET /debug/download/*
│
├── websocket/
│   └── EventBroadcaster.java   # Servidor WebSocket. Todos los emit() pasan por acá.
│
├── minecraft/                   # Lógica de Minecraft
│   ├── InstallOrchestrator.java # Coordina la instalación completa
│   ├── MinecraftLauncher.java   # Construye el proceso Java de Minecraft
│   ├── ArgumentResolver.java    # Resuelve los argumentos del cliente de Minecraft
│   ├── ClasspathBuilder.java    # Construye el classpath de librerías
│   ├── TaskBuilder.java         # Genera la lista de archivos a descargar
│   ├── InstanceManager.java     # Gestiona instancias (crear, listar, editar, borrar)
│   ├── RuntimeDownloader.java   # Descarga el runtime de Java (JVM)
│   ├── manifest/
│   │   └── ManifestClient.java  # Consulta los manifests de Mojang
│   └── models/
│       ├── VersionInfo.java     # Estructura del version manifest
│       ├── VersionManifest.java
│       └── AssetIndexManifest.java
│
├── modloader/                   # Sistema de ModLoaders Avanzado
│   ├── ModLoaderOrchestrator.java # Instalación concurrente de modloaders
│   ├── ModLoaderRegistry.java   # Registro de modloaders soportados (Fabric, Forge, etc.)
│   └── model/
│       └── InstalledLoader.java # Modelo del estado persistente del modloader
│
├── downloader/                  # Sistema de descarga
│   ├── DownloadManager.java     # Administra sesiones y el thread pool
│   ├── DownloadSession.java     # Estado de una sesión de descarga
│   ├── DownloadTask.java        # Una tarea individual (un archivo)
│   ├── DownloadResult.java      # Resultado de una tarea
│   ├── FileDownloader.java      # Descarga un archivo con progress y retry
│   └── Sha1Verifier.java        # Verifica integridad SHA-1
│
├── log/
│   └── CoreLogger.java          # Logger interno con niveles y archivo de log
│
└── util/
    └── SystemResources.java     # Detecta CPU, RAM, recomienda threads y memoria
```

---

## Flujo de instalación

Cuando llamás a `POST /install`, esto es lo que pasa internamente:

```
POST /install
    │
    ▼
InstallHandler → InstallOrchestrator.install()
    │
    ▼ (hilo virtual)
1. ManifestClient.fetchVersionById()    → descarga el version manifest de Mojang
   └── emit: install_step (resolving_version)

2. TaskBuilder.buildTasks()             → genera la lista de archivos a bajar
   └── emit: manifest_resolved
   └── emit: tasks_ready (con breakdown por categoría)

3. Si download.jvm:
   RuntimeDownloader.download()         → baja el JVM de Mojang
   └── emit: runtime_download_start / complete

4. DownloadManager.runSession()         → descarga todo en paralelo
   └── emit: session_started
   └── emit: session_progress (cada N archivos)
   └── emit: download_start / progress / complete por archivo

5. Extracción de nativos
   └── emit: install_step (extracting_natives)

6. emit: session_completed / session_failed
```

---

## Flujo de lanzamiento

```
POST /launch
    │
    ▼
LaunchHandler → MinecraftLauncher.launch()
    │
    ▼ (hilo virtual)
1. emit: launch_preparing
2. ManifestClient.fetchVersionById()    → version info para resolver argumentos
3. Resolver Java executable             → javaPath del request o el bundled
4. ClasspathBuilder.buildClasspathString() → todas las libs en el classpath
5. ArgumentResolver.resolve()           → argumentos JVM + game del version manifest
6. ProcessBuilder + spawn del proceso Java
   └── emit: launch_command_ready
   └── emit: launch_started

7. Leer stdout del proceso
   └── emit: game_log (línea por línea)

8. Al cerrar el proceso:
   └── emit: game_exited (con exitCode y status: clean/crash)
```

---

## El sistema de sesiones de descarga

El `DownloadManager` maneja un pool de threads fijo. Cada instalación crea una `DownloadSession` con su propio ID. Dentro de una sesión, todos los archivos se descargan en paralelo hasta el límite de threads.

El progress se emite por WebSocket en tiempo real. Si una descarga falla, reintenta hasta 3 veces antes de marcar el archivo como fallido.

El sistema de `sharedPath` funciona así: si un archivo ya existe en el shared path con el SHA-1 correcto, se marca como "skipped" (reutilizado) sin volver a bajarlo. Esto hace que la segunda instancia de la misma versión se "instale" en milisegundos.

---

## El cliente Node.js

El cliente tiene dos clases:

**`CoreProcess`**: spawnea el JAR como proceso hijo, parsea su stdout para detectar cuando está listo, y te da `start()` / `stop()`. Hereda de `EventEmitter` y emite `log`, `stderr` y `exit`.

**`CoreClient`**: se conecta por HTTP y WebSocket al engine. Hereda de `EventEmitter` y re-emite todos los eventos del WebSocket con sus nombres originales. Tiene métodos para cada endpoint de la API y helpers como `waitForInstall()` y `waitForGame()` que te evitan tener que escribir la lógica de polling o event listeners vos mismo.

---

## Concurrencia

El engine usa **virtual threads** de Java 21 para todo lo que es I/O: cada request HTTP corre en su propio virtual thread, cada instalación también, y cada descarga de archivo igualmente. Esto significa que podés tener muchas instalaciones simultáneas sin problemas de bloqueo.

El `DownloadManager` usa un `ThreadPoolExecutor` clásico para las descargas porque necesita un límite concreto de conexiones HTTP concurrentes.
