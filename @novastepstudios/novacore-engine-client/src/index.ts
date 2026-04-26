export { NovaCoreEngine }  from "./NovaCoreEngine.js";
export { NovaCoreClient }  from "./NovaCoreClient.js";
export { EngineProcess }   from "./EngineProcess.js";
export { InstallFlow }     from "./InstallFlow.js";
export { LaunchFlow, LaunchHandle } from "./LaunchFlow.js";
export { JvmArgsHelper } from "./JvmArgsHelper.js";

export { HttpError as NovaCoreHttpError } from "./internal/HttpClient.js";

export type { NovaCoreEngineOptions } from "./NovaCoreEngine.js";

export type { NovaCoreClientOptions } from "./NovaCoreClient.js";

export type {
    EngineProcessOptions,
    EngineProcessInfo,
} from "./EngineProcess.js";

export type {
    InstallCallbacks,
    InstallProgress,
    InstallModuleUpdate,
} from "./InstallFlow.js";

export type {
    LaunchCallbacks,
    GameLogLine,
    LogLevel,
} from "./LaunchFlow.js";

export type {
    // Requests
    InstallRequest,
    DownloadOptions,
    LaunchRequest,
    AuthConfig,
    AuthlibInjector,
    JvmConfig,
    WindowConfig,
    LauncherBranding,
    GameCustomization,
    LaunchFeatures,
    QuickPlayConfig,
    // Responses
    InstallResponse,
    LaunchResponse,
    SessionSnapshot,
    InstanceInfo,
    EngineInfo,
    // Events
    NovaCoreEvents,
    NovaCoreEventName,
    WsBaseEvent,
    // Primitives
    SessionStatus,
    ModuleStatus,
    GcPreset,
    GpuPreference,
} from "./types/index.js";