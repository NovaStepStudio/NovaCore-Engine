<div align="center">
  <img src="Docs/NovaCore-Engine.png" alt="NovaCore-Engine" width="500"/>
  <h1>NovaCore-Engine</h1>
  <p><strong>El motor de backend definitivo para launchers de Minecraft Java</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Java-21+-orange?style=flat-square"/>
    <img src="https://img.shields.io/badge/Node.js-18+-green?style=flat-square"/>
    <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-blue?style=flat-square"/>
    <img src="https://img.shields.io/badge/License-Apache%202.0-purple?style=flat-square"/>
  </p>
</div>

---

NovaCore-Engine es un motor de alto rendimiento que potencia launchers modernos (incluyendo [StepLauncher](https://github.com/NovaStepStudios/StepLauncher)). Es un proceso Java independiente que centraliza toda la lógica compleja de Minecraft —instalación, gestión de activos, resolución de dependencias y lanzamiento— exponiéndola a través de una API HTTP y eventos WebSocket en tiempo real.

Este enfoque desacoplado permite construir interfaces hermosas en **Electron, Next.js, Flutter o Java** sin tener que preocuparse por la complejidad interna del juego.

---

## 🏗️ ¿Cómo funciona? (Arquitectura)

El motor funciona como un servidor local que tu aplicación controla. No hay dependencias raras; todo se maneja a través de protocolos estándar.

- **API HTTP** (:7878) — Comandos directos: instalar, lanzar, gestionar instancias, consultar versiones.
- **WebSocket** (:7879) — Eventos: progreso granular de descargas, logs del juego, cambios de estado.

```text
Tu App / Launcher (UI)
        │
        ├── [HTTP] ──► Control de operaciones (POST /install, POST /launch)
        └── [WS]   ──◄ Feedback en tiempo real (Eventos de progreso, Logs)
              │
        NovaCore-Engine (Proceso Java independiente)
              │
        Mojang APIs + Sistema de archivos local + Minecraft Runtime
```

---

## ✨ Características Principales

- 🚀 **Rendimiento con Virtual Threads**: Basado en **Java 21 (Project Loom)** para manejar miles de conexiones y descargas I/O sin bloquear hilos.
- 🛠️ **ModLoaders Integrados**: Soporte nativo para **Forge, Fabric, Quilt y NeoForge**.
- 📂 **Branding Dinámico**: Gestión de metadatos basada en el nombre de tu launcher. Los archivos se guardan como `TuLauncher.json`, permitiendo la coexistencia de múltiples launchers.
- 🔒 **Privacidad Total**: Eliminación completa de telemetría. Tus datos y los de tus usuarios son 100% privados.
- 🧹 **Cierre Limpio (Tree Kill)**: Gestión inteligente de procesos que asegura que Minecraft y sus subprocesos se cierren correctamente, evitando procesos huérfanos.
- 📦 **Caché Compartida**: Sistema de `sharedPath` para bibliotecas y activos, ahorrando gigabytes de espacio en disco.

---

## 📋 Requisitos del Sistema

| Componente | Versión Mínima | Propósito |
| :--- | :--- | :--- |
| **Java** | 21+ | Ejecución del motor principal (Core). |
| **Node.js** | 18+ | Uso del cliente oficial y herramientas de desarrollo. |
| **Gradle** | 8+ | Compilación del motor (opcional). |

---

## 🚀 Inicio Rápido

### 1. Compilar el Engine
```bash
cd core
# En Windows
.\gradlew.bat jar
# En Linux/macOS
./gradlew jar
```
El ejecutable generado se encontrará en `core/build/libs/novacore-engine.jar`.

### 2. Instalación del Cliente
```bash
npm install @novastepstudios/novacore-engine-client
```

### 3. Ejemplo de Uso Profesional (TypeScript)
```typescript
import { NovaCoreEngine } from "@novastepstudios/novacore-engine-client";

const engine = new NovaCoreEngine({ jar: "./novacore-engine.jar" });
const client = await engine.start();

// Instalación con progreso detallado
const { sessionId } = await client.install({
    version: "1.21.1",
    instancePath: "./game",
    launcher: { name: "MiLauncher" }
});

client.on("session_progress", (p) => {
    console.log(`Progreso: ${p.overallPercent}% | Descargado: ${p.downloadedBytes} bytes`);
});

// Lanzamiento con optimización de memoria
await client.launch({
    version: "1.21.1",
    instancePath: "./game",
    jvm: { maxMemoryMb: 4096 },
    gcPreset: "g1gc_optimized"
});
```

---

## 📂 Estructura del Proyecto

```text
NovaCore-Engine/
├── core/                             # Backend Java (El motor)
│   ├── src/main/java/dev/novastep/core/
│   │   ├── Main.java                 # Punto de entrada y gestión de ciclo de vida
│   │   ├── server/                   # Servidor HTTP y Handlers de la API
│   │   ├── websocket/                # Emisión de eventos en tiempo real
│   │   ├── minecraft/                # Lógica de instalación, lanzamiento e instancias
│   │   ├── downloader/               # Sistema de descarga concurrente por sesiones
│   │   └── util/                     # Herramientas de sistema y Tree-Kill
│
├── @novastepstudios/                 # SDK de Cliente (Node.js/TS)
│   ├── src/
│   │   ├── NovaCoreEngine.ts         # Orquestador del proceso Java
│   │   ├── NovaCoreClient.ts         # Cliente API y WebSocket
│   │   └── types/                    # Definiciones de tipos completas en español
│
└── Docs/                             # Documentación técnica detallada
```

---

## 📖 Documentación Completa

| Documento | Descripción |
| :--- | :--- |
| 🏗️ [Arquitectura](Docs/01-architecture.md) | Análisis profundo del diseño interno y concurrencia. |
| 🌐 [API HTTP](Docs/03-http-api.md) | Referencia de todos los endpoints y parámetros. |
| ⚡ [Eventos WebSocket](Docs/04-websocket-events.md) | Guía completa de eventos y payloads en tiempo real. |
| 📦 [Cliente Node.js](Docs/05-nodejs-client.md) | Manual de integración del SDK en aplicaciones TS/JS. |
| 🎮 [Lanzamiento](Docs/08-launch.md) | Guía de configuración de JVM, GC y parámetros de juego. |

---

## 💡 Notas Adicionales

- **Seguridad**: El motor genera un token de acceso seguro al iniciar, garantizando que solo tu aplicación pueda controlarlo.
- **Portabilidad**: NovaCore es agnóstico a la plataforma. Funciona igual de bien en Windows, Linux y macOS.
- **Eficiencia**: Gracias a la reutilización de activos, las instalaciones de versiones que ya tienes en caché son casi instantáneas.

---

<div align="center">
  <sub>Desarrollado con pasión por <a href="https://github.com/Stepnicka012">NovaStepStudios (Alias: Stepnicka012)</a></sub>
</div>
