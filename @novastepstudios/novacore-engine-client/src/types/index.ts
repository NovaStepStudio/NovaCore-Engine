// ─────────────────────────────────────────────────────────────────────────────
// Primitivos
// ─────────────────────────────────────────────────────────────────────────────

export type SessionStatus =
    | "pending" | "running" | "paused"
    | "cancelled" | "completed" | "failed";

export type ModuleStatus =
    | "pending" | "downloading" | "completed"
    | "verifying" | "verified" | "failed" | "retrying";

/**
* Ajustes preestablecidos del recolector de basura (GC) de la JVM.
*/
export type GcPreset =
    | "auto" | "none" | "disabled" | "off"
    | "g1gc_basic" | "g1gc_optimized"
    | "zgc" | "shenandoah";

/**
* Selección de GPU PRIME (Linux).
*/
export type GpuPreference = "none" | "auto" | "dgpu" | "igpu";

// ─────────────────────────────────────────────────────────────────────────────
// Instalación
// ─────────────────────────────────────────────────────────────────────────────

export interface ModLoaderRequest {
    loader: string;
    loaderType?: string;
    loaderVersion: string;
    minecraftVersion: string;
    instancePath: string;
    sharedPath?: string;
    maxThreads?: number;
    debug?: boolean;
}

export interface DownloadOptions {
    /** Descargar el JAR del cliente de Minecraft. */
    client?: boolean;
    /** Descargar todas las librerías. */
    libraries?: boolean;
    /** Descargar assets (sonidos, texturas). */
    assets?: boolean;
    /** Descargar librerías nativas. */
    natives?: boolean;
    /** Descargar runtime de Java de Mojang. */
    jvm?: boolean;
}

export interface InstallRequest {
    /** ID de la versión de Minecraft (ej: "1.21.1"). Obligatorio. */
    version: string;
    /** Ruta absoluta al directorio de la instancia. Obligatorio. */
    instancePath: string;
    /** Ruta compartida para librerías y assets (opcional). */
    sharedPath?: string;
    /** Si true, persiste `instance.metadata.json` y `instance.config.json` en instancePath. */
    isInstance?: boolean;
    /** Opciones de descarga granulares. */
    download?: DownloadOptions;
    /** Verificar el SHA-1 de cada archivo. Default: true. */
    verifySHA1?: boolean;
    /** Hilos de descarga concurrentes. */
    maxThreads?: number;
    /** Activa logs de depuración detallados. */
    debug?: boolean;
    /** Modloader a instalar. */
    modloader?: "none" | "fabric" | "forge" | "neoforge" | "quilt" | "legacyfabric" | "optifine" | string;
    /** Versión del modloader. */
    modloaderVersion?: string;
    /** Identidad del launcher para branding. */
    launcher?: LauncherBranding;
}

export interface InstallResponse {
    sessionId: string;
    version: string;
    instancePath: string;
    status: "started";
    progress: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Ejecución (Launch)
// ─────────────────────────────────────────────────────────────────────────────

export interface AuthConfig {
    username: string;
    uuid: string;
    accessToken: string;
    /** "msa", "legacy" u "offline". */
    userType?: string;
    clientId?: string;
    xuid?: string;
}

export interface AuthlibInjector {
    enabled: boolean;
    jarPath: string;
    serverUrl: string;
}

export interface JvmConfig {
    /** Memoria mínima (Xms) en MB. */
    minMemoryMb?: number;
    /** Memoria máxima (Xmx) en MB. */
    maxMemoryMb?: number;
    /** Argumentos JVM adicionales al final. */
    extraArgs?: string[];
    /** Argumentos JVM adicionales al inicio. */
    prependArgs?: string[];
}

export interface WindowConfig {
    width?: number;
    height?: number;
    fullscreen?: boolean;
}

export interface LauncherBranding {
    /** Nombre de tu launcher, visible en Minecraft. */
    name: string;
    /** Versión de tu launcher. */
    version?: string;
}

export interface GameCustomization {
    /** Sobrescribe el directorio de trabajo del juego (--gameDir). */
    gameDir?: string;
    /** Argumentos adicionales para Minecraft. */
    extraGameArgs?: string[];
    /** Propiedades del sistema (-Dkey=value). */
    extraJvmProperties?: Record<string, string>;
    /** Conexión automática a un servidor al arrancar. */
    serverHost?: string;
    serverPort?: number;
}

export interface QuickPlayConfig {
    mode: "singleplayer" | "multiplayer" | "realms";
    value: string;
}

export interface LaunchFeatures {
    demo?: boolean;
    quickPlay?: QuickPlayConfig;
}

export interface LaunchRequest {
    /** ID de la versión a lanzar. Obligatorio. */
    version: string;
    /** Ruta absoluta al directorio de la instancia. Obligatorio. */
    instancePath: string;
    /** Ruta compartida (si se usó en la instalación). */
    sharedPath?: string;
    /** Ruta manual al ejecutable de Java (opcional). */
    javaPath?: string;
    /** Activa/desactiva aceleración por hardware. */
    hardwareAcceleration?: boolean;
    /** Preset de GC. */
    gcPreset?: GcPreset;
    /** Preferencia de GPU (Linux PRIME). */
    gpuPreference?: GpuPreference;
    /** Credenciales de autenticación. */
    auth?: AuthConfig;
    /** Configuración de Authlib Injector. */
    authlibInjector?: AuthlibInjector;
    /** Ajustes de la JVM. */
    jvm?: JvmConfig;
    /** Ajustes de ventana. */
    window?: WindowConfig;
    /** Branding del launcher. */
    launcher?: LauncherBranding;
    /** Personalización del juego. */
    game?: GameCustomization;
    /** Funciones especiales (Demo, QuickPlay). */
    features?: LaunchFeatures;
}

export interface LaunchResponse {
    launchId: string;
    version: string;
    status: "started";
}

// ─────────────────────────────────────────────────────────────────────────────
// Estados y Resultados
// ─────────────────────────────────────────────────────────────────────────────

export interface SessionSnapshot {
    sessionId: string;
    status: SessionStatus;
    createdAt: number;
    totalFiles: number;
    completedFiles: number;
    skippedFiles: number;
    failedFiles: number;
    pendingFiles: number;
    totalBytes: number;
    downloadedBytes: number;
    overallPercent: number;
    error?: string;
}

export interface InstanceInfo {
    launchId: string;
    version: string;
    username: string;
    instancePath: string;
    startedAt: number;
    pid: number;
    status: "starting" | "running" | "stopping" | "stopped";
    exitCode: number;
    logFile: string | null;
}

export interface EngineInfo {
    version: string;
    cpu: { cores: number; optimalDlThreads: number };
    ram: { totalMb: number; estimatedFreeMb: number; reservedForOsMb: number };
    recommended: {
        downloadThreads: number;
        mcMinRamMb: number;
        mcMaxRamMb: number;
        gcPreset: string;
    };
}

// ── Telemetry & Sessions ──────────────────────────────────────────────────
export interface CrashContext {
    instanceId: string;
    exitCode: number;
    source: string;
    reason: string;
    context: string[];
    timestamp: number;
}

export interface SessionRecord {
    launchId: string;
    instancePath: string;
    version: string;
    durationMs: number;
    startedAt: number;
    exitCode: number;
}

export interface WorldMetadata {
    folderName: string;
    levelName: string;
    lastPlayed: number;
    gameType: number;
    versionName: string;
    path: string;
    instanceId: string;
    iconBase64: string | null;
}

export interface WorldListResponse {
    worlds: WorldMetadata[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Mapa de Eventos WebSocket
// ─────────────────────────────────────────────────────────────────────────────

export interface WsBaseEvent {
    event: string;
    data: unknown;
    ts: number;
}

/** Mapa completo de eventos emitidos por el engine Java. */
export interface NovaCoreEvents {
    connected: { message: string; version: string };

    // ── Pasos de instalación ───────────────────────────────────────
    install_step: {
        sessionId: string;
        step: string;
        [key: string]: unknown;
    };
    module_status: {
        sessionId: string;
        module: "client" | "libraries" | "assets" | "natives";
        status: ModuleStatus;
    };
    manifest_resolved: { sessionId: string; versionId: string };
    offline_mode: { sessionId: string; version: string; reason: string };
    tasks_ready: {
        sessionId: string;
        totalTasks: number;
        totalBytes: number;
        offline: boolean;
        breakdown: { client: number; libraries: number; assets: number; natives: number; asset_index: number };
    };
    install_completed: { sessionId: string; version: string; modloader: string };
    install_failed: { sessionId: string; reason: string; modules: Record<string, ModuleStatus> };

    // ── Sesiones de descarga ───────────────────────────────────────
    session_started: { session: string; totalFiles: number; totalBytes: number };
    session_progress: {
        sessionId: string;
        completedFiles: number;
        skippedFiles: number;
        totalFiles: number;
        overallPercent: number;
        downloadedBytes: number;
        totalBytes: number;
    };
    session_completed: { sessionId: string; totalFiles: number; downloadedBytes: number };
    session_failed: { sessionId: string; reason: string };
    session_paused: { session: string };
    session_resumed: { session: string };
    session_cancelled: { session: string };

    // ── Eventos de archivos ────────────────────────────────────────
    download_start: { sessionId: string; category: string; file: string; size: number };
    download_progress: { sessionId: string; category: string; file: string; downloaded: number; total: number };
    download_complete: { sessionId: string; category: string; file: string; bytes: number; skipped: boolean };
    download_error: { sessionId: string; category: string; file: string; error: string };
    sha1_check: { sessionId: string; file: string; ok: boolean; expected: string; computed: string };

    // ── Lanzamiento ────────────────────────────────────────────────
    launch_preparing: { launchId: string; version: string };
    launch_starting: { launchId: string; mainClass: string; version: string };
    launch_started: { launchId: string; pid: number; logFile: string };
    launch_failed: { launchId: string; error: string };
    launch_verification_failed: { launchId: string; missing: string[]; hint: string };
    launch_exited: {
        launchId: string;
        exitCode: number;
        normal: boolean;
        durationMs: number;
    };
    game_crash: { launchId: string; exitCode: number; reason: string; context?: string[]; source?: string; timestamp?: number };

    // ── Logs del juego ─────────────────────────────────────────────
    game_log: {
        launchId: string; line: string; stream: "stdout" | "stderr";
        level: string; logger: string; message: string;
    };
    game_stdout: { launchId: string; line: string };
    game_stderr: { launchId: string; line: string };

    // ── Procesadores ───────────────────────────────────────────────
    modloader_processor_log: { sessionId: string; line: string };

    // ── Otros ───────────────────────────────────────────────────────
    debug: { sessionId?: string; message: string };
    recovery_state: { sessions: SessionSnapshot[] };
}

export type NovaCoreEventName = keyof NovaCoreEvents;
