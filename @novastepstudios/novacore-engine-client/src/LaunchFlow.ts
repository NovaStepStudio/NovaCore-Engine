import type { WsClient } from "./internal/WsClient.js";
import type { HttpClient } from "./internal/HttpClient.js";
import type { LaunchRequest, NovaCoreEvents } from "./types/index.js";

export type LogLevel = "INFO" | "WARN" | "ERROR" | "FATAL" | "DEBUG" | string;

export interface GameLogLine {
    raw: string;
    level: LogLevel;
    logger: string;
    message: string;
    stream: "stdout" | "stderr";
}

export interface LaunchCallbacks {
    /** Fired when the game process is running (has a PID). */
    onStart?: (launchId: string, pid: number) => void;
    /** Fired for every game log line. */
    onLog?: (line: GameLogLine) => void;
    /** Fired only for WARN log lines (subset of onLog). */
    onWarn?: (line: string, logger: string) => void;
    /** Fired only for ERROR/FATAL log lines (subset of onLog). */
    onError?: (line: string, logger: string) => void;
    /** Fired when the game exits normally (exitCode 0). */
    onExit?: (launchId: string, durationMs: number) => void;
    /**
    * Fired when the game exits with a non-zero code.
    * Also fires `onExit` after this.
    */
    onCrashExit?: (launchId: string, exitCode: number, reason: string) => void;
    /**
    * Fired if the engine rejected the launch before the process started
    * (e.g. missing files detected by MinecraftVerifier).
    */
    onLaunchFailed?: (error: string, missing?: string[]) => void;
}

/**
* High-level Minecraft launch flow with log streaming and crash detection.
*
* Abstracts all WebSocket subscriptions for launch events and returns a
* handle you can use to kill the instance if needed.
*
* @example
* ```ts
* const flow = new LaunchFlow(client.ws, client.http);
*
* const handle = await flow.run(
*   {
*     version:      "1.21.4",
*     instancePath: "/instances/1.21.4",
*     auth:         { username: "Player", uuid: "…", accessToken: "…" },
*     jvm:          { maxMemoryMb: 4096 },
*     gcPreset:     "g1gc_optimized",
*   },
*   {
*     onStart:     (id, pid) => console.log("Game running, PID:", pid),
*     onLog:       ({ level, message }) => appendLog(level, message),
*     onCrash:     (desc)  => showCrashScreen(desc),
*     onCrashExit: (id, code, reason) => showCrashDialog(code, reason),
*     onExit:      (id, ms) => console.log(`Stopped after ${ms}ms`),
*   }
* );
*
* // Kill if needed:
* await handle.kill();
* ```
*/
export class LaunchFlow {
    constructor(
        private readonly ws: WsClient,
        private readonly http: HttpClient,
    ) { }

    /**
    * Launches Minecraft and attaches event listeners.
    * Resolves with a {@link LaunchHandle} as soon as the process has a PID.
    * The handle's `exited` promise resolves when the game exits.
    */
    async run(req: LaunchRequest, callbacks?: LaunchCallbacks): Promise<LaunchHandle> {
        const res = await this.http.launch(req);
        const { launchId } = res;

        return new Promise<LaunchHandle>((resolveHandle, rejectHandle) => {

            let resolved = false;
            let exitedResolve = (_: void) => { };
            const exited = new Promise<void>(r => { exitedResolve = r; });

            const cleanup = () => {
                this.ws.off("launch_started", onStarted);
                this.ws.off("launch_failed", onFailed);
                this.ws.off("launch_verification_failed", onVerifyFailed);
                this.ws.off("game_log", onLog);
                // this.ws.off("game_log_warn",             onWarn);
                // this.ws.off("game_log_error",            onLogError);
                this.ws.off("launch_exited", onExited);
                this.ws.off("game_crash", onGameCrash);
            };

            // ── launch_started → resolve handle ──────────────────────────────
            const onStarted = (d: NovaCoreEvents["launch_started"]) => {
                if (d.launchId !== launchId || resolved) return;
                resolved = true;
                callbacks?.onStart?.(launchId, d.pid);
                resolveHandle(new LaunchHandle(launchId, this.http, cleanup, exited));
            };

            // ── launch failures (before process starts) ───────────────────────
            const onFailed = (d: NovaCoreEvents["launch_failed"]) => {
                if (d.launchId !== launchId) return;
                cleanup();
                callbacks?.onLaunchFailed?.(d.error);
                rejectHandle(new Error(d.error));
            };

            const onVerifyFailed = (d: NovaCoreEvents["launch_verification_failed"]) => {
                if (d.launchId !== launchId) return;
                cleanup();
                callbacks?.onLaunchFailed?.(
                    `Missing components: ${d.missing.join(", ")}. ${d.hint}`,
                    d.missing,
                );
                rejectHandle(new Error(`Launch verification failed: ${d.missing.join(", ")}`));
            };

            const onLog = (d: NovaCoreEvents["game_log"]) => {
                if (d.launchId !== launchId) return;

                const logLine: GameLogLine = {
                    raw: d.line,
                    level: d.level,
                    logger: d.logger,
                    message: d.message,
                    stream: d.stream,
                };

                callbacks?.onLog?.(logLine);

                // Filtrado por niveles para callbacks específicos
                if (d.level === "WARN") {
                    callbacks?.onWarn?.(d.line, d.logger);
                } else if (d.level === "ERROR" || d.level === "FATAL") {
                    callbacks?.onError?.(d.line, d.logger);
                }
            };

            const onGameCrash = (d: NovaCoreEvents["game_crash"]) => {
                if (d.launchId !== launchId) return;
                // onCrashExit is also fired from onExited below (non-normal exit)
                callbacks?.onCrashExit?.(launchId, d.exitCode, d.reason);
            };

            const onExited = (d: NovaCoreEvents["launch_exited"]) => {
                if (d.launchId !== launchId) return;
                cleanup();
                callbacks?.onExit?.(launchId, d.durationMs);
                exitedResolve();
            };

            this.ws.on("launch_started", onStarted);
            this.ws.on("launch_failed", onFailed);
            this.ws.on("launch_verification_failed", onVerifyFailed);
            this.ws.on("game_log", onLog);
            this.ws.on("launch_exited", onExited);
            this.ws.on("game_crash", onGameCrash);
        });
    }
}

/**
* A handle to a running Minecraft instance.
* Returned by {@link LaunchFlow.run}.
*/
export class LaunchHandle {
    /** The engine's launchId for this instance. */
    readonly launchId: string;

    /**
    * A promise that resolves when the game process exits (for any reason).
    *
    * @example
    * ```ts
    * const handle = await flow.run(req, callbacks);
    * await handle.exited;
    * console.log("Game closed");
    * ```
    */
    readonly exited: Promise<void>;

    private readonly _http: HttpClient;
    private readonly _cleanup: () => void;

    constructor(launchId: string, http: HttpClient, cleanup: () => void, exited: Promise<void>) {
        this.launchId = launchId;
        this._http = http;
        this._cleanup = cleanup;
        this.exited = exited;
    }

    /** Force-kills the running Minecraft process. */
    async kill(): Promise<void> {
        await this._http.killInstance(this.launchId);
        this._cleanup();
    }
}