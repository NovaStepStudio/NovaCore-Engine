import { WsClient } from "./internal/WsClient.js";
import { HttpClient } from "./internal/HttpClient.js";
import { InstallFlow } from "./InstallFlow.js";
import { LaunchFlow } from "./LaunchFlow.js";
import type {
    InstallRequest, LaunchRequest,
    NovaCoreEventName, NovaCoreEvents,
    SessionSnapshot, InstanceInfo, EngineInfo,
} from "./types/index.js";
import type { InstallCallbacks } from "./InstallFlow.js";
import type { LaunchCallbacks, LaunchHandle } from "./LaunchFlow.js";

export interface NovaCoreClientOptions {
    /** URL base del servidor HTTP. @default "http://localhost:7878" */
    httpUrl?: string;
    /** URL del servidor WebSocket. @default "ws://localhost:7879" */
    wsUrl?: string;
    /** Token de acceso generado por el motor. Obligatorio. */
    token: string;
    /** Tiempo de espera de peticiones HTTP en ms. @default 30_000 */
    timeoutMs?: number | undefined;
    /** Reconexión automática del WebSocket si se pierde la conexión. @default true */
    autoReconnect?: boolean | undefined;
}

/**
* Cliente unificado para NovaCore engine.
*
* Tras llamar a `connect()`, puedes instalar versiones, lanzar Minecraft
* y reaccionar a eventos en tiempo real con tipado completo de TypeScript.
*/
export class NovaCoreClient {
    /** @internal */
    readonly _ws: WsClient;
    /** @internal */
    readonly _http: HttpClient;

    private readonly installFlow: InstallFlow;
    private readonly launchFlow: LaunchFlow;

    constructor(opts: NovaCoreClientOptions) {
        this._http = new HttpClient(
            opts.httpUrl ?? "http://localhost:7878",
            opts.token,
            opts.timeoutMs ?? 30_000,
        );
        this._ws = new WsClient({
            url: opts.wsUrl ?? "ws://localhost:7879",
            token: opts.token,
            autoReconnect: opts.autoReconnect ?? true,
        });
        this.installFlow = new InstallFlow(this._ws, this._http);
        this.launchFlow = new LaunchFlow(this._ws, this._http);
    }

    /**
    * Abre la conexión WebSocket. Se resuelve cuando el motor confirma el acceso.
    * Debe llamarse antes de suscribirse a eventos o realizar peticiones.
    */
    connect(): Promise<void> { return this._ws.connect(); }

    /** Cierra el WebSocket de forma permanente. */
    disconnect(): void { this._ws.close(); }

    /** Indica si el cliente está conectado actualmente. */
    get isConnected(): boolean { return this._ws.connected; }

    /** Suscribe un controlador a un evento específico del motor. */
    on<K extends NovaCoreEventName>(event: K, handler: (data: NovaCoreEvents[K]) => void): this {
        this._ws.on(event, handler); return this;
    }
    /** Elimina una suscripción. */
    off<K extends NovaCoreEventName>(event: K, handler: (data: NovaCoreEvents[K]) => void): this {
        this._ws.off(event, handler); return this;
    }
    /** Suscripción de un solo uso. */
    once<K extends NovaCoreEventName>(event: K, handler: (data: NovaCoreEvents[K]) => void): this {
        this._ws.once(event, handler); return this;
    }
    /** Escucha todos los eventos (útil para sistemas de logging globales). */
    onAny(handler: (event: NovaCoreEventName, data: unknown) => void): this {
        this._ws.onAny(handler); return this;
    }
    /** Retorna una Promesa que se resuelve en la siguiente ocurrencia del evento. */
    waitFor<K extends NovaCoreEventName>(event: K, timeoutMs?: number): Promise<NovaCoreEvents[K]> {
        return this._ws.waitFor(event, timeoutMs);
    }

    /**
    * Instala una versión de Minecraft con callbacks de progreso.
    * 
    * Este es el método recomendado para instalar: gestiona las suscripciones
    * automáticamente y se resuelve/rechaza al terminar todo el flujo.
    */
    install(req: InstallRequest, callbacks?: InstallCallbacks, timeoutMs?: number): Promise<void> {
        return this.installFlow.run(req, callbacks, timeoutMs);
    }

    /** Pausa una sesión de descarga activa. */
    pauseInstall(sessionId: string): Promise<void> { return this._http.pauseInstall(sessionId); }
    /** Reanuda una sesión pausada. */
    resumeInstall(sessionId: string): Promise<void> { return this._http.resumeInstall(sessionId); }
    /** Cancela una sesión de descarga. */
    cancelInstall(sessionId: string): Promise<void> { return this._http.cancelInstall(sessionId); }

    /**
    * Lanza Minecraft con streaming de logs y callbacks de ciclo de vida.
    * Retorna un {@link LaunchHandle} en cuanto el proceso tiene un PID asignado.
    */
    launch(req: LaunchRequest, callbacks?: LaunchCallbacks): Promise<LaunchHandle> {
        return this.launchFlow.run(req, callbacks);
    }

    /** Mata forzosamente una instancia en ejecución (y sus hijos). */
    killInstance(launchId: string): Promise<void> { return this._http.killInstance(launchId); }
    /** Retorna el estado de todas las instancias en ejecución. */
    getRunningInstances(): Promise<InstanceInfo[]> { return this._http.getRunningInstances(); }
    /** Retorna detalles de una instancia en ejecución específica. */
    getRunningInstance(launchId: string) { return this._http.getRunningInstance(launchId); }

    // ── Telemetry & QuickPlay ───────────────────────────────────────────────
    getLatestCrash() { return this._http.getLatestCrash(); }
    getSessions() { return this._http.getSessions(); }
    getWorlds(): Promise<import("./types/index.js").WorldListResponse> { return this._http.getWorlds(); }

    /** Consulta una captura (snapshot) de una sesión (vía HTTP). */
    getSession(sessionId: string): Promise<SessionSnapshot | null> {
        return this._http.getSession(sessionId);
    }
    /** Consulta sesiones interrumpidas que pueden ser recuperadas. */
    async getRecoverySessions(): Promise<SessionSnapshot[]> {
        const r = await this._http.getRecoverySessions();
        return r.snapshots;
    }

    /** Obtiene el resumen total del progreso de todas las instalaciones activas. */
    getGlobalProgress() { return this._http.getGlobalProgress(); }

    /** Información de versión, hardware y optimizaciones recomendadas. */
    getEngineInfo(): Promise<EngineInfo> { return this._http.getEngineInfo(); }

    // ── ModLoaders ──────────────────────────────────────────────────────────
    getModLoaders(): Promise<{ loaders: string[] }> { return this._http.getModLoaders(); }
    getModLoaderVersions(loader: string, mcVersion: string) { return this._http.getModLoaderVersions(loader, mcVersion); }
    installModLoader(req: import("./types/index.js").ModLoaderRequest) { return this._http.installModLoader(req); }
    getModLoaderState(instancePath: string) { return this._http.getModLoaderState(instancePath); }
    deleteModLoaderState(instancePath: string) { return this._http.deleteModLoaderState(instancePath); }

    // ── Runtime ─────────────────────────────────────────────────────────────
    downloadRuntime(version: string, instancePath: string, sharedPath?: string) { return this._http.downloadRuntime(version, instancePath, sharedPath); }

    // ── System ──────────────────────────────────────────────────────────────
    closeEngine(): Promise<void> { return this._http.close(); }
}