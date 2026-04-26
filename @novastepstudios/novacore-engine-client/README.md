# @novastepstudios/novacore-engine-client

TypeScript ESM client for **novacore-engine** — the Java core powering StepLauncher.

Provides a fully typed API over the engine's HTTP REST endpoints and WebSocket event bus, with zero runtime dependencies beyond Node.js 18+.

---

## Installation

```bash
git clone https://github.com/Stepnicka012/NovaCore-Engine.git
cd NovaCore-Engine/@novastepstudios/novacore-engine-client

bun i
```

---

## Quick start

```ts
import { NovaCoreClient } from "@novastepstudios/novacore-engine-client";

const client = new NovaCoreClient({ token: process.env.CORE_TOKEN! });

// Subscribe to typed events BEFORE connecting
client.on("module_status", ({ module, status }) => {
  console.log(`[${module}] → ${status}`);
});

client.on("session_progress", ({ overallPercent, downloadedBytes }) => {
  console.log(`${overallPercent}% — ${(downloadedBytes / 1e6).toFixed(1)} MB`);
});

client.on("install_completed", ({ version }) => {
  console.log("Ready to launch:", version);
});

// Connect WebSocket
await client.connect();

// Start install
const { sessionId } = await client.install({
  version:      "1.21.4",
  instancePath: "/home/user/.novastep/instances/1.21.4",
  sharedPath:   "/home/user/.novastep/shared",   // shared libraries + assets
  modloader:    "fabric",
  modloaderVersion: "0.16.0",
  download: { jvm: false },                       // use system Java
});

console.log("Session:", sessionId);
```

---

## Launching Minecraft

```ts
const { launchId } = await client.launch({
  version:      "fabric-loader-0.16.0-1.21.4",
  instancePath: "/home/user/.novastep/instances/1.21.4",
  sharedPath:   "/home/user/.novastep/shared",

  auth: {
    username:    "Player",
    uuid:        "00000000-0000-0000-0000-000000000000",
    accessToken: "token",
    userType:    "msa",
  },

  jvm: { maxMemoryMb: 4096 },

  // GC: "auto"|"none"|"g1gc_basic"|"g1gc_optimized"|"zgc"|"shenandoah"
  gcPreset: "g1gc_optimized",

  // Hardware acceleration: true = on (default), false = disabled
  hardwareAcceleration: true,

  // GPU preference for Linux PRIME: "auto"|"dgpu"|"igpu"
  gpuPreference: "auto",

  launcher: { name: "StepLauncher", version: "2.0.0" },
});

// Stream game logs
client.on("game_log", ({ line, level, logger }) => {
  console.log(`[${level}] [${logger}] ${line}`);
});

client.on("launch_exited", ({ exitCode, durationMs }) => {
  console.log(`Exited ${exitCode} after ${durationMs}ms`);
});
```

---

## Install and wait

```ts
// Blocking convenience helper with typed result
try {
  const result = await client.installAndWait(
    { version: "1.21.4", instancePath: "..." },
    300_000 // 5 min timeout
  );
  console.log("Done:", result.version);
} catch (err) {
  console.error("Install failed:", err.message);
}
```

---

## API reference

### `NovaCoreClient`

```ts
new NovaCoreClient(opts: {
  httpUrl?:      string;   // default "http://localhost:7878"
  wsUrl?:        string;   // default "ws://localhost:7879"
  token:         string;   // required — matches --access-token on engine
  timeoutMs?:    number;   // HTTP timeout, default 30 000
  autoReconnect?: boolean; // WS auto-reconnect, default true
})
```

| Method | Description |
|---|---|
| `connect()` | Open WebSocket. Resolves on `connected` event. |
| `disconnect()` | Close permanently. |
| `on(event, handler)` | Subscribe to typed event. Chainable. |
| `off(event, handler)` | Unsubscribe. |
| `once(event, handler)` | One-shot subscription. |
| `onAny(handler)` | Wildcard listener for all events. |
| `waitFor(event, ms?)` | Promise resolving on next event occurrence. |
| `install(req)` | Start async install, returns `{ sessionId }`. |
| `installAndWait(req, ms?)` | Install + wait for completion. |
| `pauseInstall(sessionId)` | Pause active download. |
| `resumeInstall(sessionId)` | Resume paused download. |
| `cancelInstall(sessionId)` | Cancel download. |
| `launch(req)` | Launch Minecraft, returns `{ launchId }`. |
| `killInstance(launchId)` | Force-kill game process. |
| `getInstances()` | List running instances. |
| `getSession(sessionId)` | Poll session snapshot (HTTP). |
| `getRecoverySessions()` | Interrupted sessions needing recovery. |
| `getEngineInfo()` | Engine version + system resource recommendations. |

---

## WebSocket events

All events are emitted via `.on(eventName, handler)`.

### Install lifecycle

| Event | Key fields |
|---|---|
| `install_step` | `sessionId`, `step` |
| `module_status` | `sessionId`, `module`, `status` |
| `tasks_ready` | `sessionId`, `totalTasks`, `breakdown` |
| `install_completed` | `sessionId`, `version`, `modloader` |
| `install_failed` | `sessionId`, `reason`, `modules` |

**`module_status` values:** `pending → downloading → completed → verifying → verified` (or `failed → retrying`)

### Download progress

| Event | Key fields |
|---|---|
| `session_progress` | `overallPercent`, `downloadedBytes`, `totalBytes` |
| `session_completed` | `sessionId`, `totalFiles`, `downloadedBytes` |
| `session_failed` | `sessionId`, `reason` |
| `download_complete` | `category`, `file`, `bytes`, `skipped` |
| `sha1_check` | `file`, `ok`, `expected`, `computed` |

### Launch + game

| Event | Key fields |
|---|---|
| `launch_started` | `launchId`, `pid`, `logFile` |
| `game_log` | `launchId`, `line`, `level`, `logger`, `stream` |
| `game_log_error` | `launchId`, `line`, `logger` |
| `game_crash` | `launchId`, `exitCode`, `reason` |
| `launch_exited` | `launchId`, `exitCode`, `normal`, `durationMs` |

### Telemetry (opt-in)

| Event | Key fields |
|---|---|
| `telemetry` | `launchId`, `ramMb`, `heapTotalMb`, `systemFreeMb` |
| `telemetry_fps` | `launchId`, `fps` |
| `telemetry_world_loaded` | `launchId` |
| `game_crash_report` | `launchId`, `description`, `report` |

---

## GC preset reference

| Value | JVM flags | Use when |
|---|---|---|
| `"auto"` / `undefined` | none | Let JVM choose |
| `"none"` / `"disabled"` | none | Explicitly disable |
| `"g1gc_basic"` | G1GC + 50ms pause | ≤ 2 GB RAM |
| `"g1gc_optimized"` | G1GC tuned | 2–6 GB RAM ✓ recommended |
| `"zgc"` | ZGC generational | ≥ 6 GB RAM |
| `"shenandoah"` | Shenandoah IU | Low-latency builds |

---

## Java resolution order

When `javaPath` is not set, the engine searches in order:

1. Mojang runtime downloaded via `download.jvm: true`
2. `java.home` system property → `bin/java`
3. `java` on PATH (probed via `java -version`)

No Mojang runtime is required. System Java works out of the box.

---

## License

MIT — NovaStepStudios
