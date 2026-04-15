<div align="center">
  <img src="docs/NovaCore-Engine.png" alt="NovaCore-Engine" width="500"/>
  <p><strong>Motor de backend para launchers de Minecraft Java</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Java-21+-orange?style=flat-square"/>
    <img src="https://img.shields.io/badge/Node.js-18+-green?style=flat-square"/>
    <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-blue?style=flat-square"/>
    <img src="https://img.shields.io/badge/License-Apache%202.0-purple?style=flat-square"/>
  </p>
</div>

---

NovaCore-Engine es el motor interno que potencia [StepLauncher](https://github.com/NovaStepStudios/StepLauncher). Es un proceso Java independiente que expone toda la lógica de Minecraft —instalación, descarga, lanzamiento, gestión de instancias— a través de una API HTTP local y un WebSocket de eventos en tiempo real.

La idea es simple: tu launcher o aplicación habla con el engine a través de HTTP y WebSocket. El engine se encarga de todo lo pesado. Vos solo consumís los eventos y mostrás lo que está pasando en pantalla.

El cliente Node.js incluido [@novastepstudios/novacore-engine-client](./@novastepstudios/novacore-engine-client) te conecta al engine en segundos, con soporte completo para TypeScript a través de los tipos incluidos.

---

## ¿Cómo funciona?

El engine corre como un proceso Java separado de tu aplicación. Internamente levanta dos servicios:

- **HTTP en el puerto 7878** — endpoints REST para instalar, lanzar, crear instancias, consultar versiones y más.
- **WebSocket en el puerto 7879** — eventos en tiempo real: progreso de descargas, logs del juego, errores, etc.

Tu código (Node.js, Electron, Java puro, o lo que quieras) se conecta a esos puertos y controla todo desde ahí. No hay dependencias raras, es solo HTTP y WebSocket estándar.

```
Tu app / Launcher
    │
    ├── HTTP  :7878  → instalar, lanzar, instancias, versiones...
    └── WS    :7879  → progreso, logs, eventos en tiempo real
         │
    novacore-engine.jar (proceso Java en background)
         │
    Mojang APIs + Sistema de archivos local
```

---

## Requisitos

| Componente | Versión mínima | Para qué                                     |
| ---------- | -------------- | -------------------------------------------- |
| Java       | 21+            | Correr el engine                             |
| Node.js    | 18+            | Usar el cliente JS/TS                        |
| Gradle     | 8+             | Compilar el engine (solo si lo compilás vos) |

---

## Inicio rápido

### 1. Compilar el engine

```bash
cd core

# Linux / macOS
./gradlew jar

# Windows
gradlew.bat jar
# Oh
./build.bat
```

El JAR queda en `core/build/libs/novacore-engine.jar`.

### 2. Instalar el cliente Node.js

```bash
cd client
npm install
```

### 3. Configurar y correr un ejemplo

Editá `client/src/examples/config.js` con la ruta al JAR y tus directorios. Después:

```bash
npm run example:sysinfo    # Info del sistema y versiones disponibles
npm run example:instances  # Crear y gestionar instancias
npm run example:install    # Instalar una versión de Minecraft
npm run example:launch     # Lanzar Minecraft vanilla
npm run example:advanced   # Opciones avanzadas de lanzamiento
npm run example:full       # Flujo completo: instalar y lanzar
```

---

## Documentación

La documentación completa está en la carpeta [`docs/`](docs/):

| Documento                                           | Qué cubre                                            |
| --------------------------------------------------- | ---------------------------------------------------- |
| [Arquitectura](docs/01-architecture.md)             | Cómo está estructurado el engine por dentro          |
| [Compilar el Engine](docs/02-building.md)           | Guía paso a paso para compilar el JAR                |
| [API HTTP](docs/03-http-api.md)                     | Todos los endpoints con ejemplos de request/response |
| [Eventos WebSocket](docs/04-websocket-events.md)    | Todos los eventos que emite el engine                |
| [Cliente Node.js](docs/05-nodejs-client.md)         | Cómo integrar el cliente en tu proyecto              |
| [Instancias](docs/06-instances.md)                  | Sistema de gestión de instancias                     |
| [Instalación](docs/07-install.md)                   | Cómo funciona el sistema de instalación              |
| [Lanzamiento](docs/08-launch.md)                    | Opciones de lanzamiento, auth, JVM, GC               |
| [Integración con Java](docs/09-java-integration.md) | Usar el engine desde Java puro                       |
| [Referencia de Tipos](docs/10-type-reference.md)    | Todas las interfaces TypeScript                      |

---

## Estructura del proyecto

```
NovaCore-Engine/
├── core/                             # Engine Java (el backend)
│   ├── build.gradle
│   └── src/main/java/dev/novastep/core/
│       ├── Main.java                 # Entry point y startup
│       ├── server/                   # HTTP server + handlers por endpoint
│       ├── websocket/                # EventBroadcaster (servidor WebSocket)
│       ├── minecraft/                # Instalación, lanzamiento, instancias
│       ├── downloader/               # Descarga concurrente con sesiones
│       ├── log/                      # Logger interno con rotación
│       └── util/                     # SystemResources y utilidades
│
├── client/                           # Cliente Node.js
│   ├── package.json
│   ├── minecraft-core.d.ts           # Tipos TypeScript completos
│   └── src/
│       ├── CoreProcess.js            # Spawnea y gestiona el proceso Java
│       ├── CoreClient.js             # Cliente HTTP + WebSocket
│       └── examples/                 # Ejemplos listos para correr
│
└── docs/                             # Documentación completa
```

---

## Notas rápidas

- El engine solo escucha en `localhost` por diseño. No está pensado para exponerse a la red.
- Los puertos 7878 y 7879 son configurables si tenés conflictos con otros procesos.
- El sistema de `sharedPath` te permite que múltiples instancias compartan assets y librerías sin duplicar archivos en disco.

---

<div align="center">
  <sub>Hecho con 🔥 por <a href="https://github.com/NovaStepStudios">NovaStepStudios</a></sub>
</div>
