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
    verifySHA1?:       boolean;
    maxThreads?:       number;
    debug?:            boolean;
    modloader?:        string;
    modloaderVersion?: string;
}

export interface LaunchOptions {
    version:               string;
    instancePath:          string;
    sharedPath?:           string;
    javaPath?:             string;
    hardwareAcceleration?: boolean;
    gcPreset?:             'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
    gpuPreference?:        'auto' | 'dgpu' | 'igpu';
    auth?:                 AuthConfig;
    authlibInjector?:      AuthlibInjectorConfig;
    jvm?:                  JvmConfig;
    window?:               WindowConfig;
    launcher?:             LauncherBranding;
    features?:             LaunchFeatures;
    game?:                 GameCustomization;
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
    gameDir?:            string;
    extraGameArgs?:      string[];
    extraJvmProperties?: Record<string, string>;
    disableMultiplayer?: boolean;
    disableChat?:        boolean;
    serverHost?:         string;
    serverPort?:         number;
}

export interface CreateInstanceOptions {
    name:         string;
    mcVersion:    string;
    config?:      InstanceConfig;
    autoInstall?: boolean;
    install?:     AutoInstallConfig;
}

export interface AutoInstallConfig {
    sharedPath?: string;
    download?: {
        client?:    boolean;
        libraries?: boolean;
        assets?:    boolean;
        natives?:   boolean;
        jvm?:       boolean;
    };
    verifySHA1?: boolean;
    maxThreads?: number;
}

export interface InstanceConfig {
    modLoader?:          string;
    javaPath?:           string;
    minMemoryMb?:        number;
    maxMemoryMb?:        number;
    hardwareAccel?:      boolean;
    gcPreset?:           'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
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

export interface RuntimeDownloadOptions {
    version:      string;
    instancePath: string;
    sharedPath?:  string;
}

export interface RuntimeDownloadResponse {
    status:       'downloading';
    version:      string;
    instancePath: string;
    runtimeDir:   string;
    shared:       boolean;
    message:      string;
}

export interface SessionSnapshot {
    sessionId:       string;
    status:          'pending' | 'running' | 'completed' | 'failed';
    createdAt:       number;
    totalFiles:      number;
    completedFiles:  number;
    skippedFiles:    number;
    failedFiles:     number;
    pendingFiles:    number;
    overallPercent:  number;
    downloadedBytes: number;
    totalBytes:      number;
    error?:          string;
}

export interface ModLoaderInstallOptions {
    loader:           string;
    loaderVersion?:   string;
    minecraftVersion: string;
    instancePath:     string;
    sharedPath?:      string;
    maxThreads?:      number;
    debug?:           boolean;
}

export interface ModLoaderInstallResponse {
    sessionId: string;
    loader:    string;
    mcVersion: string;
    status:    'started';
}

export interface InstalledLoaderState {
    loaderType:       string;
    loaderVersion:    string;
    minecraftVersion: string;
    versionJsonId:    string;
    installerJarPath: string | null;
    installedAt:      number;
}

export interface LoaderVersion {
    loaderVersion:    string;
    minecraftVersion: string;
    stable:           boolean;
}

export interface ModLoaderVersionsResponse {
    versions: LoaderVersion[];
}

export interface ModLoaderListResponse {
    loaders: string[];
}

export type LaunchExitInfo     = { launchId: string; exitCode: number };
export type ModLoaderInstalled = { sessionId: string; loader: string; loaderVersion: string; versionJsonId: string };

export declare class CoreClient {
    constructor(opts: {
        accessToken:    string;
        host?:          string;
        httpPort?:      number;
        wsPort?:        number;
        maxReconnects?: number;
    });

    readonly host:        string;
    readonly httpPort:    number;
    readonly wsPort:      number;
    readonly accessToken: string;
    readonly baseUrl:     string;
    readonly wsUrl:       string;
    readonly state:       'disconnected' | 'connecting' | 'connected' | 'reconnecting';
    readonly connected:   boolean;

    connect(): Promise<void>;
    disconnect(): void;

    isOnline(): Promise<boolean>;
    startConnectivityMonitor(intervalMs?: number): void;
    stopConnectivityMonitor(): void;

    apiInfo(): Promise<Record<string, unknown>>;
    systemResources(): Promise<Record<string, unknown>>;
    versions(type?: string | null): Promise<{ versions: Array<{ id: string; type: string; releaseTime: string }> }>;
    progress(sessionId: string): Promise<SessionSnapshot>;
    allSessions(): Promise<SessionSnapshot[]>;
    launchStatus(launchId: string): Promise<{ launchId: string; running: boolean; pid?: number }>;
    debugCategory(category: string, sessionId?: string): Promise<Record<string, unknown>>;

    install(opts: InstallOptions): Promise<{ sessionId: string }>;
    launch(opts: LaunchOptions): Promise<{ launchId: string }>;
    killLaunch(launchId: string): Promise<{ killed: boolean }>;
    downloadRuntime(opts: RuntimeDownloadOptions): Promise<RuntimeDownloadResponse>;
    downloadRuntime(version: string, instancePath: string, sharedPath?: string): Promise<RuntimeDownloadResponse>;

    listInstances(): Promise<{ instances: unknown[] }>;
    getInstance(idOrName: string): Promise<unknown>;
    getInstancePath(idOrName: string): Promise<{ path: string }>;
    deleteInstance(idOrName: string): Promise<{ deleted: boolean }>;
    updateInstance(idOrName: string, updates: Partial<InstanceConfig>): Promise<unknown>;
    createInstance(opts: CreateInstanceOptions): Promise<{ id: string; name: string; path: string }>;

    waitForInstall(sessionId: string, onProgress?: (snap: SessionSnapshot) => void): Promise<SessionSnapshot>;
    waitForGame(launchId: string): Promise<LaunchExitInfo>;

    listModLoaders(): Promise<ModLoaderListResponse>;
    getModLoaderVersions(loaderName: string, mcVersion: string): Promise<ModLoaderVersionsResponse>;
    installModLoader(opts: ModLoaderInstallOptions): Promise<ModLoaderInstallResponse>;
    getModLoaderState(instancePath: string): Promise<InstalledLoaderState>;
    deleteModLoaderState(instancePath: string): Promise<{ removed: boolean }>;
    waitForModLoader(sessionId: string, onProgress?: (p: { loader: string; files: number }) => void): Promise<ModLoaderInstalled>;

    onEvent(eventType: string, handler: (data: unknown) => void): () => void;
    onGameLog(launchId: string, handler: (line: string) => void): () => void;

    on(event: 'connectivity:change',        listener: (online: boolean) => void): this;
    on(event: 'ws:connected',               listener: () => void): this;
    on(event: 'ws:disconnected',            listener: (data: { code: number; reason: string }) => void): this;
    on(event: 'offline:install',            listener: (data: { version: string }) => void): this;
    on(event: 'offline:launch',             listener: (data: { username: string }) => void): this;

    on(event: 'install_step',               listener: (data: { sessionId: string; step: string; [k: string]: unknown }) => void): this;
    on(event: 'tasks_ready',                listener: (data: { sessionId: string; totalTasks: number; offline: boolean; breakdown: Record<string, number> }) => void): this;
    on(event: 'offline_mode',               listener: (data: { sessionId: string; version: string; reason: string }) => void): this;

    on(event: 'session_progress',           listener: (data: SessionSnapshot) => void): this;
    on(event: 'session_completed',          listener: (data: SessionSnapshot) => void): this;
    on(event: 'session_failed',             listener: (data: { sessionId: string; reason: string }) => void): this;

    on(event: 'launch_preparing',           listener: (data: { launchId: string; version: string }) => void): this;
    on(event: 'launch_verification_failed', listener: (data: { launchId: string; missing: string[]; hint: string }) => void): this;
    on(event: 'launch_starting',            listener: (data: { launchId: string; mainClass: string; version: string }) => void): this;
    on(event: 'launch_started',             listener: (data: { launchId: string; pid: number }) => void): this;
    on(event: 'launch_exited',              listener: (data: LaunchExitInfo) => void): this;
    on(event: 'launch_failed',              listener: (data: { launchId: string; error: string }) => void): this;
    on(event: 'launch_log_file',            listener: (data: { launchId: string; logFile: string }) => void): this;

    on(event: 'game_stdout',                listener: (data: { launchId: string; line: string }) => void): this;
    on(event: 'game_stderr',                listener: (data: { launchId: string; line: string }) => void): this;
    on(event: 'game_log',                   listener: (data: { launchId: string; line: string; stream: 'stdout' | 'stderr' }) => void): this;

    on(event: 'modloader_resolving',        listener: (data: { sessionId: string; loader: string; loaderVersion: string; mcVersion: string }) => void): this;
    on(event: 'modloader_downloading',      listener: (data: { sessionId: string; loader: string; files: number }) => void): this;
    on(event: 'modloader_processor',        listener: (data: { sessionId: string; step: number; total: number; jar: string }) => void): this;
    on(event: 'modloader_install_start',    listener: (data: { sessionId: string; loader: string; version: string }) => void): this;
    on(event: 'modloader_install_done',     listener: (data: { sessionId: string; loader: string; versionId: string }) => void): this;
    on(event: 'modloader_installed',        listener: (data: ModLoaderInstalled) => void): this;

    on(event: string,                       listener: (...args: unknown[]) => void): this;

    off(event: string, listener: (...args: unknown[]) => void): this;
    emit(event: string, ...args: unknown[]): boolean;
}

export declare class CoreProcess {
    constructor(opts: {
        jarPath:      string;
        port?:        number;
        wsPort?:      number;
        javaPath?:    string;
        maxMemoryMb?: number;
        debug?:       boolean;
    });

    start(): Promise<{ accessToken: string; httpPort: number; wsPort: number }>;
    stop(): Promise<void>;
    isRunning(): boolean;

    readonly port:   number;
    readonly wsPort: number;
}

export { CoreClient as default };
