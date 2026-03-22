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

export interface LaunchOptions {
  version:      string;
  instancePath: string;
  sharedPath?:  string;
  javaPath?:    string;

  auth?:            AuthConfig;
  authlibInjector?: AuthlibInjectorConfig;
  jvm?:             JvmConfig;
  window?:          WindowConfig;
  launcher?:        LauncherBranding;
  features?:        LaunchFeatures;
  game?:            GameCustomization;

  hardwareAcceleration?: boolean;
  gpuPreference?:        'auto' | 'dgpu' | 'igpu';
  gcPreset?:             'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
}

export interface AuthConfig {
  username?:     string;
  uuid?:         string;
  accessToken?:  string;
  userType?:     'msa' | 'legacy' | 'offline';
  clientId?:     string;
  xuid?:         string;
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
  demo?:       boolean;
  quickPlay?:  {
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

export interface CreateInstanceOptions {
  name:        string;
  mcVersion:   string;
  config?:     InstanceConfig;
  autoInstall?: boolean;
  install?:    AutoInstallConfig;
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

export interface InstanceConfig {
  modLoader?:         'vanilla' | 'fabric' | 'forge' | 'neoforge' | 'quilt' | 'liteloader';
  modLoaderVersion?:  string;
  javaPath?:          string;
  minMemoryMb?:       number;
  maxMemoryMb?:       number;
  hardwareAccel?:     boolean;
  gcPreset?:          'auto' | 'g1gc_basic' | 'g1gc_optimized' | 'zgc' | 'shenandoah';
  jvmArgs?:           string[];
  extraGameArgs?:     string[];
  jvmProperties?:     Record<string, string>;
  launcherName?:      string;
  launcherVersion?:   string;
  serverHost?:        string;
  serverPort?:        number;
  disableMultiplayer?: boolean;
  disableChat?:       boolean;
  customGameDir?:     string;
}

export interface RuntimeDownloadOptions {
  version:      string;
  instancePath: string;
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
  totalBytes:      number;
  downloadedBytes: number;
  overallPercent:  number;
  error?:          string;
}

export interface InstallResponse {
  sessionId:    string;
  version:      string;
  instancePath: string;
  status:       'started';
  progress:     string;
  websocket:    string;
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

export interface CreateInstanceResponse {
  id:               string;
  name:             string;
  path:             string;
  installSessionId?: string;
  installStatus?:   'started';
  installProgress?: string;
}

export interface InstanceInfo {
  id:              string;
  name:            string;
  mcVersion:       string;
  modLoader:       string;
  modLoaderVersion?: string;
  minMemoryMb:     number;
  maxMemoryMb:     number;
  hardwareAccel:   boolean;
  gcPreset:        string | null;
  launcherName:    string | null;
  launcherVersion: string | null;
  serverHost:      string | null;
  serverPort:      number | null;
  jvmArgs:         string[];
  extraGameArgs:   string[];
  createdAt:       string;
  lastPlayedAt:    string | null;
  totalPlayHours:  string;
  path:            string;
  installed:       boolean;
}

export interface SystemResourcesResponse {
  cpu: {
    cores:             number;
    optimalDlThreads:  number;
  };
  ram: {
    totalMb:           number;
    estimatedFreeMb:   number;
    reservedForOsMb:   number;
  };
  recommended: {
    downloadThreads:   number;
    mcMinRamMb:        number;
    mcMaxRamMb:        number;
    gcPreset:          'g1gc_basic' | 'g1gc_optimized' | 'zgc';
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
    install:      string;
    launch:       string;
    launch_kill:  string;
    launch_status: string;
    progress:     string;
    sessions:     string;
    versions:     string;
    runtime:      string;
    instances:    string;
    system:       string;
    debug:        { client: string; libraries: string; assets: string; natives: string };
  };
}

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
  summary:   { done: number; skipped: number; failed: number; pending: number; downloading: number };
  files:     DebugFileEntry[];
}

export type FileCategory = 'client' | 'library' | 'asset' | 'native' | 'asset_index' | 'runtime';

export type InstallStep =
  | 'resolving_version'
  | 'fetching_asset_index'
  | 'downloading_jvm'
  | 'building_task_list'
  | 'downloading'
  | 'extracting_natives';

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

export interface CoreEvents {
  'connected':               { message: string; version: string };
  'install_step':            { sessionId: string; step: InstallStep; [key: string]: unknown };
  'manifest_resolved':       { sessionId: string; version: string };
  'tasks_ready':             { sessionId: string; totalTasks: number; breakdown: TaskBreakdown };
  'session_started':         { session: string; totalFiles: number; totalBytes: number };
  'session_progress':        SessionProgress;
  'session_completed':       { session: string; totalFiles: number; totalBytes: number };
  'session_failed':          { session: string; reason: string };
  'download_start':          { sessionId: string; category: FileCategory; file: string; total: number };
  'download_progress':       { sessionId: string; category: FileCategory; file: string; downloaded: number; total: number; percent: number };
  'download_complete':       { sessionId: string; category: FileCategory; file: string; bytes: number; skipped: boolean };
  'download_error':          { sessionId: string; category: FileCategory; file: string; error: string };
  'sha1_check':              { sessionId: string; file: string; passed: boolean; expected: string; computed: string };
  'runtime_download_start':  { session: string; component: string; javaVersion: string; totalFiles: number };
  'runtime_download_complete': { session: string; javaVersion: string; javaPath: string };
  'runtime_ready':           { version: string; component: string; javaPath: string };
  'runtime_error':           { version: string; error: string };
  'launch_preparing':        { launchId: string; version: string };
  'launch_command_ready':    { launchId: string; command: string[]; mainClass: string; javaExec: string; offline: boolean };
  'launch_started':          { launchId: string; version: string; username: string; gameDir: string; authlib: boolean; javaExec: string; offline: boolean };
  'launch_failed':           { launchId: string; error: string };
  'game_log':                { launchId: string; line: string };
  'game_exited':             { launchId: string; exitCode: number; status: 'clean' | 'crash' };
  'debug':                   { sessionId: string; message: string };
  'ws:disconnected':         void;
}

export declare class CoreClient {
  constructor(opts?: { host?: string; httpPort?: number; wsPort?: number });

  connect(): Promise<void>;
  disconnect(): void;

  apiInfo(): Promise<ApiInfoResponse>;
  systemResources(): Promise<SystemResourcesResponse>;
  versions(type?: 'release' | 'snapshot' | 'old_alpha' | 'old_beta' | null): Promise<VersionsResponse>;
  install(opts: InstallOptions): Promise<InstallResponse>;
  progress(sessionId: string): Promise<SessionSnapshot>;
  allSessions(): Promise<{ count: number; sessions: SessionSnapshot[] }>;
  launch(opts: LaunchOptions): Promise<LaunchResponse>;
  killLaunch(launchId: string): Promise<{ launchId: string; status: 'killed' }>;
  launchStatus(launchId: string): Promise<{ launchId: string; running: boolean; status: string }>;
  downloadRuntime(version: string, instancePath: string): Promise<unknown>;
  debugCategory(category: FileCategory, sessionId?: string | null): Promise<DebugResponse>;

  createInstance(opts: CreateInstanceOptions): Promise<CreateInstanceResponse>;
  listInstances(): Promise<{ count: number; instances: InstanceInfo[] }>;
  getInstance(idOrName: string): Promise<InstanceInfo>;
  getInstancePath(idOrName: string): Promise<{ id: string; path: string }>;
  updateInstance(idOrName: string, updates: Partial<InstanceConfig>): Promise<{ updated: boolean; id: string }>;
  deleteInstance(idOrName: string): Promise<{ deleted: boolean; id: string }>;

  waitForInstall(sessionId: string, onProgress?: (snap: SessionSnapshot) => void): Promise<SessionSnapshot>;
  waitForGame(launchId: string): Promise<{ launchId: string; exitCode: number; status: 'clean' | 'crash' }>;

  onEvent<K extends keyof CoreEvents>(event: K, handler: (data: CoreEvents[K]) => void): () => void;
  onGameLog(launchId: string, handler: (line: string) => void): () => void;

  on<K extends keyof CoreEvents>(event: K, listener: (data: CoreEvents[K]) => void): this;
  once<K extends keyof CoreEvents>(event: K, listener: (data: CoreEvents[K]) => void): this;
  off<K extends keyof CoreEvents>(event: K, listener: (data: CoreEvents[K]) => void): this;
}

export declare class CoreProcess {
  constructor(opts?: {
    jarPath?:      string;
    javaPath?:     string;
    httpPort?:     number;
    wsPort?:       number;
    threads?:      number;
    jvmArgs?:      string[];
    verbose?:      boolean;
    instancesDir?: string;
    logDir?:       string;
    launcherName?: string;
    logLevel?:     'DEBUG' | 'INFO' | 'WARN' | 'ERROR';
  });

  start(): Promise<void>;
  stop(): Promise<void>;

  readonly running: boolean;
  readonly pid: number | undefined;

  on(event: 'log',    listener: (line: string) => void): this;
  on(event: 'stderr', listener: (line: string) => void): this;
  on(event: 'exit',   listener: (code: number) => void): this;
  on(event: 'ready',  listener: () => void): this;
}
