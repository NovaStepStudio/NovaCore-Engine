import type {
    InstallRequest, InstallResponse,
    LaunchRequest, LaunchResponse,
    SessionSnapshot, InstanceInfo, EngineInfo,
} from "../types/index.js";

export class HttpError extends Error {
    constructor(public readonly status: number, message: string) {
        super(message);
        this.name = "NovaCoreHttpError";
    }
}

export class HttpClient {
    constructor(
        private readonly base: string,
        private readonly token: string,
        private readonly timeoutMs: number,
    ) {}
    
    // ── Install ─────────────────────────────────────────────────────────────
    install(req: InstallRequest)        { return this.post<InstallResponse>("/install", req); }
    pauseInstall(id: string)            { return this.post<void>(`/install/pause/${id}`, null); }
    resumeInstall(id: string)           { return this.post<void>(`/install/resume/${id}`, null); }
    cancelInstall(id: string)           { return this.post<void>(`/install/cancel/${id}`, null); }
    getRecoverySessions()               { return this.get<{ count: number; snapshots: SessionSnapshot[] }>("/install/recovery"); }
    
    // ── Launch ──────────────────────────────────────────────────────────────
    launch(req: LaunchRequest)          { return this.post<LaunchResponse>("/launch", req); }
    killInstance(id: string)            { return this.post<void>(`/launch/kill/${id}`, null); }
    getInstances()                      { return this.get<any>("/launch/instances").then(r => r.instances ?? []); }
    getInstance(id: string)             { return this.get<InstanceInfo>(`/launch/instances/${id}`).catch(e => { if (e instanceof HttpError && e.status === 404) return null; throw e; }); }
    
    // ── Progress ────────────────────────────────────────────────────────────
    getSession(id: string)              { return this.get<SessionSnapshot>(`/progress?sessionId=${id}`).catch(e => { if (e instanceof HttpError && e.status === 404) return null; throw e; }); }
    getSummary()                        { return this.get<any>("/progress/summary"); }
    
    // ── Engine info ─────────────────────────────────────────────────────────
    getEngineInfo()                     { return this.get<EngineInfo>("/system/resources"); }

    // ── System ──────────────────────────────────────────────────────────────
    close()                             { return this.post<void>("/close", null); }
    
    // ── Core ────────────────────────────────────────────────────────────────
    private async get<T>(path: string): Promise<T>             { return this.req<T>("GET", path, null); }
    private async post<T>(path: string, body: unknown): Promise<T> { return this.req<T>("POST", path, body); }
    
    private async req<T>(method: string, path: string, body: unknown): Promise<T> {
        const ctrl  = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), this.timeoutMs);
        let res: Response;
        try {
            const init: RequestInit = {
                method,
                signal: ctrl.signal,
                headers: {
                    "Content-Type": "application/json",
                    "X-Access-Token": this.token,
                },
            };
            // Solo incluir body si no es null
            if (body !== null) {
                init.body = JSON.stringify(body);
            }
            res = await fetch(`${this.base}${path}`, init);
        } catch (e) {
            throw new HttpError(0, `Network: ${e instanceof Error ? e.message : String(e)}`);
        } finally {
            clearTimeout(timer);
        }
        let json: unknown;
        try { json = await res.json(); } catch { json = {}; }
        if (!res.ok) throw new HttpError(res.status, (json as Record<string, string>)["error"] ?? `HTTP ${res.status}`);
        return json as T;
    }
}