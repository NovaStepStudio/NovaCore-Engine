import { EventEmitter } from 'events';

// ─── Install ────────────────────────────────────────────────────────────────

export interface InstallOptions {
    version:      string;
    instancePath: string;
    sharedPath?:  string;
    download?: {
        client?:    boolean;
        libraries?: boolean;
        assets?:    boolean;
        natives?:   boolean;
        jvm?:       boolean;
    };
    verifySHA1?:  boolean;
    maxThreads?:  number;
    debug?:       boolean;
}

export interface InstallResponse {
    sessionId:    string;
    version:      string;
    instancePath: string;
    status:       'started';
    progress:     string;
    websocket:    string;
}

// ─── Launch ─────────────────────────────────────────────────────────────────

export interface LaunchOptions {
    version:      string;
    instancePath: string;
    sharedPath?:  string;
    javaPath?:    string;
    
    auth?:                 AuthConfig;
    authlibInjector?:      AuthlibInjectorConfig;
    jvm?:                  JvmConfig;
    window?:               WindowConfig;
    launcher?:             LauncherBranding;
    features?:             LaunchFeatures;
    game?:                 GameCustomization;
    
    hardwareAcceleration?: boolean;
    gpuPreference?:        'auto' | 'dgpu' | 'igpu';
    gcPreset?:             GcPreset;
}

export interface AuthConfig {
    username?:    string;
    uuid?:        string;
    accessToken?: string;
    userType?:    'msa' | 'legacy' | 'offline';
    clientId?:    string;
    xuid?:        string;
}

export interface AuthlibInjectorConfig {
    enabled:   boolean;
    jarPath:   string;
    serverUrl: string;
}

export interface JvmConfig {
    minMemoryMb?:  number;
    maxMemoryMb?:  number;
    extraArgs?:    string[];
    prependArgs?:  string[];
}

export interface WindowConfig {
    width?:      number;
    height?:     number;
    fullscreen?: boolean;
}

export interface LauncherBranding {
    name?:    string;
    version?: string;
}

export interface LaunchFeatures {
    demo?:      boolean;
    quickPlay?: {
        mode:  'singleplayer' | 'multiplayer' | 'realms';
        value: string;
    };
}

export interface GameCustomization {
    gameDir?:             string;
    extraGameArgs?:       string[];
    extraJvmProperties?:  Record<string, string>;
    disableMultiplayer?:  boolean;
    disableChat?:         boolean;
    serverHost?:          string;
    serverPort?:          number;
}

export interface LaunchResponse {
    launchId:        string;
    status:          'launching';
    version:         string;
    username:        string;
    instancePath:    string;
    authlibInjector: { enabled: boolean; server?: string };
    message:         string;
    kill:            string;
}

// ─── Instances ───────────────────────────────────────────────────────────────

export type GcPreset = 'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
export type ModLoader = 'vanilla' | 'fabric' | 'forge' | 'neoforge' | 'quilt' | 'liteloader';

export interface InstanceConfig {
    modLoader?:          ModLoader;
    modLoaderVersion?:   string;
    javaPath?:           string;
    minMemoryMb?:        number;
    maxMemoryMb?:        number;
    hardwareAccel?:      boolean;
    gcPreset?:           GcPreset;
    jvmArgs?:            string[];
    extraGameArgs?:      string[];
    jvmProperties?:      Record<string, string>;
    launcherName?:       string;
    launcherVersion?:    string;
    serverHost?:         string;
    serverPort?:         number;
    disableMultiplayer?: boolean;
    disableChat?:        boolean;
    customGameDir?:      string;
}

export interface AutoInstallConfig {
    sharedPath?:  string;
    download?: {
        client?:    boolean;
        libraries?: boolean;
        assets?:    boolean;
        natives?:   boolean;
        jvm?:       boolean;
    };
    verifySHA1?:  boolean;
    maxThreads?:  number;
}

export interface CreateInstanceOptions {
    name:         string;
    mcVersion:    string;
    config?:      InstanceConfig;
    autoInstall?: boolean;
    install?:     AutoInstallConfig;
}

export interface CreateInstanceResponse {
    id:               string;
    name:             string;
    path:             string;
    installSessionId?: string;
    installStatus?:   'started';
    installProgress?: string;
}

export interface InstanceInfo {
    id:               string;
    name:             string;
    mcVersion:        string;
    modLoader:        ModLoader;
    modLoaderVersion?: string;
    minMemoryMb:      number;
    maxMemoryMb:      number;
    hardwareAccel:    boolean;
    gcPreset:         string | null;
    launcherName:     string | null;
    launcherVersion:  string | null;
    serverHost:       string | null;
    serverPort:       number | null;
    jvmArgs:          string[];
    extraGameArgs:    string[];
    createdAt:        string;
    lastPlayedAt:     string | null;
    totalPlayHours:   string;
    path:             string;
    installed:        boolean;
}

// ─── Progress / Sessions ─────────────────────────────────────────────────────

export interface SessionSnapshot {
    sessionId:       string;
    status:          'pending' | 'running' | 'completed' | 'failed';
    createdAt:       number;
    totalFiles:      number;
    completedFiles:  number;
    skippedFiles:    number;
    failedFiles:     number;
    pendingFiles:    number;
    totalBytes:      number;
    downloadedBytes: number;
    overallPercent:  number;
    error?:          string;
}

export interface SessionProgress {
    session:         string;
    completedFiles:  number;
    skippedFiles:    number;
    totalFiles:      number;
    percent:         number;
    downloadedBytes: number;
    totalBytes:      number;
}

export interface TaskBreakdown {
    client:      number;
    libraries:   number;
    assets:      number;
    natives:     number;
    asset_index: number;
}

// ─── System / Versions ───────────────────────────────────────────────────────

export interface SystemResourcesResponse {
    cpu: {
        cores:            number;
        optimalDlThreads: number;
    };
    ram: {
        totalMb:          number;
        estimatedFreeMb:  number;
        reservedForOsMb:  number;
    };
    recommended: {
        downloadThreads:  number;
        mcMinRamMb:       number;
        mcMaxRamMb:       number;
        gcPreset:         'g1gc_basic' | 'g1gc_optimized' | 'zgc';
    };
}

export interface VersionEntry {
    id:          string;
    type:        'release' | 'snapshot' | 'old_alpha' | 'old_beta';
    releaseTime: string;
    url:         string;
}

export interface VersionsResponse {
    latest:   { release: string; snapshot: string };
    count:    number;
    filter?:  string;
    versions: VersionEntry[];
}

export interface ApiInfoResponse {
    name:      string;
    vendor:    string;
    version:   string;
    java:      string;
    os:        string;
    endpoints: {
        install:       string;
        launch:        string;
        launch_kill:   string;
        launch_status: string;
        progress:      string;
        sessions:      string;
        versions:      string;
        runtime:       string;
        instances:     string;
        system:        string;
        debug: {
            client:    string;
            libraries: string;
            assets:    string;
            natives:   string;
        };
    };
}

// ─── Debug ───────────────────────────────────────────────────────────────────

export type FileCategory = 'client' | 'library' | 'asset' | 'native' | 'asset_index' | 'runtime';

export interface DebugFileEntry {
    file:        string;
    progress:    number;
    size:        number;
    downloaded:  number;
    destination: string;
    status:      'pending' | 'downloading' | 'done' | 'skipped' | 'failed';
    url:         string;
    error?:      string;
}

export interface DebugResponse {
    sessionId: string;
    category:  FileCategory;
    total:     number;
    summary: {
        done:        number;
        skipped:     number;
        failed:      number;
        pending:     number;
        downloading: number;
    };
    files: DebugFileEntry[];
}

// ─── WebSocket Events ────────────────────────────────────────────────────────

export type InstallStep =
| 'resolving_version'
| 'fetching_asset_index'
| 'downloading_jvm'
| 'building_task_list'
| 'downloading'
| 'extracting_natives';

export interface CoreEvents {
    // Conexión
    'connected':               { message: string; version: string };
    
    // Instalación
    'install_step':            { sessionId: string; step: InstallStep; [key: string]: unknown };
    'manifest_resolved':       { sessionId: string; version: string };
    'tasks_ready':             { sessionId: string; totalTasks: number; breakdown: TaskBreakdown };
    'session_started':         { session: string; totalFiles: number; totalBytes: number };
    'session_progress':        SessionProgress;
    'session_completed':       { session: string; totalFiles: number; totalBytes: number };
    'session_failed':          { session: string; reason: string };
    
    // Descarga por archivo
    'download_start':          { sessionId: string; category: FileCategory; file: string; total: number };
    'download_progress':       { sessionId: string; category: FileCategory; file: string; downloaded: number; total: number; percent: number };
    'download_complete':       { sessionId: string; category: FileCategory; file: string; bytes: number; skipped: boolean };
    'download_error':          { sessionId: string; category: FileCategory; file: string; error: string };
    'sha1_check':              { sessionId: string; file: string; passed: boolean; expected: string; computed: string };
    
    // Runtime Java
    'runtime_download_start':    { session: string; component: string; javaVersion: string; totalFiles: number };
    'runtime_download_complete': { session: string; javaVersion: string; javaPath: string };
    'runtime_ready':             { version: string; component: string; javaPath: string };
    'runtime_error':             { version: string; error: string };
    
    // Lanzamiento
    'launch_preparing':        { launchId: string; version: string };
    'launch_command_ready':    { launchId: string; command: string[]; mainClass: string; javaExec: string; offline: boolean };
    'launch_started':          { launchId: string; version: string; username: string; gameDir: string; authlib: boolean; javaExec: string; offline: boolean };
    'launch_failed':           { launchId: string; error: string };
    'game_log':                { launchId: string; line: string };
    'game_exited':             { launchId: string; exitCode: number; status: 'clean' | 'crash' };
    
    // Debug / interno
    'debug':                   { sessionId: string; message: string };
    
    // Solo en el cliente Node.js
    'ws:disconnected':         void;
    'ws:error':                Error;
}

// ─── CoreProcess ─────────────────────────────────────────────────────────────

export interface CoreProcessOptions {
    /** Ruta al JAR del engine. Default: ../../core/build/libs/novacore-engine.jar */
    jarPath?:      string;
    /** Ejecutable Java. Default: 'java' */
    javaPath?:     string;
    /** Puerto HTTP. Default: 7878 */
    httpPort?:     number;
    /** Puerto WebSocket. Default: 7879 */
    wsPort?:       number;
    /** Threads de descarga. 0 = auto. Default: 32 */
    threads?:      number;
    /** Args JVM para el proceso del engine. Default: ['-Xms32m', '-Xmx128m', '-XX:+UseG1GC'] */
    jvmArgs?:      string[];
    /** Si true, re-emite todo el stdout del engine como eventos 'log'. Default: false */
    verbose?:      boolean;
    /** Directorio raíz de instancias. Default: null */
    instancesDir?: string;
    /** Directorio de logs. Default: null */
    logDir?:       string;
    /** Nombre del launcher. Default: null */
    launcherName?: string;
    /** Nivel de log del engine. Default: null */
    logLevel?:     'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
}

export declare class CoreProcess extends EventEmitter {
    constructor(opts?: CoreProcessOptions);
    
    /** Spawnea el proceso Java y espera a que esté listo. */
    start(): Promise<void>;
    
    /** Detiene el engine (SIGTERM → SIGKILL si no responde en 3s). */
    stop(): Promise<void>;
    
    /** true si el proceso está vivo. */
    readonly running: boolean;
    
    /** PID del proceso Java, undefined si no está corriendo. */
    readonly pid: number | undefined;
    
    on(event: 'log',    listener: (line: string) => void): this;
    on(event: 'stderr', listener: (line: string) => void): this;
    on(event: 'exit',   listener: (code: number) => void): this;
    on(event: 'ready',  listener: () => void): this;
}

// ─── CoreClient ──────────────────────────────────────────────────────────────

export interface CoreClientOptions {
    /** Host del engine. Default: 'localhost' */
    host?:     string;
    /** Puerto HTTP. Default: 7878 */
    httpPort?: number;
    /** Puerto WebSocket. Default: 7879 */
    wsPort?:   number;
}

export declare class CoreClient extends EventEmitter {
    constructor(opts?: CoreClientOptions);
    
    // Conexión
    connect(): Promise<void>;
    disconnect(): void;
    
    // Info
    apiInfo(): Promise<ApiInfoResponse>;
    systemResources(): Promise<SystemResourcesResponse>;
    versions(type?: 'release' | 'snapshot' | 'old_alpha' | 'old_beta' | null): Promise<VersionsResponse>;
    
    // Instalación
    install(opts: InstallOptions): Promise<InstallResponse>;
    progress(sessionId: string): Promise<SessionSnapshot>;
    allSessions(): Promise<{ count: number; sessions: SessionSnapshot[] }>;
    waitForInstall(sessionId: string, onProgress?: (snap: SessionSnapshot) => void): Promise<SessionSnapshot>;
    
    // Lanzamiento
    launch(opts: LaunchOptions): Promise<LaunchResponse>;
    killLaunch(launchId: string): Promise<{ launchId: string; status: 'killed' }>;
    launchStatus(launchId: string): Promise<{ launchId: string; running: boolean; status: string }>;
    waitForGame(launchId: string): Promise<{ launchId: string; exitCode: number; status: 'clean' | 'crash' }>;
    onGameLog(launchId: string, handler: (line: string) => void): () => void;
    
    // Instancias
    createInstance(opts: CreateInstanceOptions): Promise<CreateInstanceResponse>;
    listInstances(): Promise<{ count: number; instances: InstanceInfo[] }>;
    getInstance(idOrName: string): Promise<InstanceInfo>;
    getInstancePath(idOrName: string): Promise<{ id: string; path: string }>;
    updateInstance(idOrName: string, updates: Partial<InstanceConfig>): Promise<{ updated: boolean; id: string }>;
    deleteInstance(idOrName: string): Promise<{ deleted: boolean; id: string }>;
    
    // Runtime
    downloadRuntime(version: string, instancePath: string): Promise<unknown>;
    
    // Debug
    debugCategory(category: FileCategory, sessionId?: string | null): Promise<DebugResponse>;
    
    // Eventos tipados
    onEvent<K extends keyof CoreEvents>(event: K, handler: (data: CoreEvents[K]) => void): () => void;
    
    // EventEmitter tipado
    on<K extends keyof CoreEvents>(event: K, listener: (data: CoreEvents[K]) => void): this;
    /** Escucha todos los eventos. handler recibe (eventName, data). */
    on(event: '*', listener: (eventName: string, data: unknown) => void): this;
    once<K extends keyof CoreEvents>(event: K, listener: (data: CoreEvents[K]) => void): this;
    off<K extends keyof CoreEvents>(event: K, listener: (data: CoreEvents[K]) => void): this;
}
