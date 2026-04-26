import type { NovaCoreEventName, NovaCoreEvents, WsBaseEvent } from "../types/index.js";

type Callback<K extends NovaCoreEventName> = (data: NovaCoreEvents[K], raw: WsBaseEvent) => void;
type AnyCallback = (data: unknown, raw: WsBaseEvent) => void;

export interface WsClientOptions {
    url: string;
    token: string;
    autoReconnect?: boolean;
    reconnectDelay?: number;
    maxReconnectAttempts?: number;
}

export class WsClient {
    private readonly opts: Required<WsClientOptions>;
    private ws: WebSocket | null = null;
    private attempts = 0;
    private timer: ReturnType<typeof setTimeout> | null = null;
    private dead = false;
    private readonly map = new Map<NovaCoreEventName | "*", Set<AnyCallback>>();
    private heartbeatTimer: ReturnType<typeof setInterval> | null = null;

    constructor(opts: WsClientOptions) {
        this.opts = {
            autoReconnect: true,
            reconnectDelay: 1500,
            maxReconnectAttempts: 0,
            ...opts,
        };
    }

    connect(): Promise<void> {
        return new Promise((resolve, reject) => {
            const url = `${this.opts.url}?token=${encodeURIComponent(this.opts.token)}`;
            try { this.ws = new WebSocket(url); }
            catch (e) { reject(e); return; }

            this.once("connected", () => resolve());

            this.ws.onopen = () => {
                this.attempts = 0;
                this.startHeartbeat();
            };
            this.ws.onmessage = (e) => this.dispatch(e.data as string);
            this.ws.onerror = (e) => {
                reject(e);
                // Notamos que onerror suele ir seguido de onclose, 
                // así que dejamos que onclose maneje la reconexión.
            };
            this.ws.onclose = (e) => {
                // Si el código es 1008 (Policy Violation/Unauthorized), 
                // significa que el token es inválido. No tiene sentido reintentar.
                if (e.code === 1008) {
                    console.error("[WsClient] Conexión rechazada: Token inválido. Deteniendo reconexión.");
                    this.dead = true;
                    return;
                }
                this.stopHeartbeat();
                this.scheduleReconnect();
            };
        });
    }

    close(): void {
        this.dead = true;
        this.stopHeartbeat();
        if (this.timer) { clearTimeout(this.timer); this.timer = null; }
        this.ws?.close(1000, "bye");
        this.ws = null;
    }

    get connected(): boolean { return this.ws?.readyState === WebSocket.OPEN; }

    on<K extends NovaCoreEventName>(event: K, cb: Callback<K>): this {
        this.add(event, cb as AnyCallback); return this;
    }
    off<K extends NovaCoreEventName>(event: K, cb: Callback<K>): this {
        this.map.get(event)?.delete(cb as AnyCallback); return this;
    }
    once<K extends NovaCoreEventName>(event: K, cb: Callback<K>): this {
        const w: AnyCallback = (d, r) => { this.off(event, w as unknown as Callback<K>); (cb as AnyCallback)(d, r); };
        this.add(event, w); return this;
    }
    onAny(cb: (event: NovaCoreEventName, data: unknown) => void): this {
        this.add("*", (d, r) => cb(r.event as NovaCoreEventName, d)); return this;
    }
    waitFor<K extends NovaCoreEventName>(event: K, ms = 30_000): Promise<NovaCoreEvents[K]> {
        return new Promise((res, rej) => {
            const t = setTimeout(() => { this.off(event, cb); rej(new Error(`Timeout: "${event}"`)); }, ms);
            const cb: Callback<K> = (d) => { clearTimeout(t); res(d); };
            this.once(event, cb);
        });
    }

    private add(k: NovaCoreEventName | "*", cb: AnyCallback) {
        if (!this.map.has(k)) this.map.set(k, new Set());
        this.map.get(k)!.add(cb);
    }
    private dispatch(raw: string) {
        let p: WsBaseEvent;
        try { p = JSON.parse(raw) as WsBaseEvent; } catch { return; }
        const key = p.event as NovaCoreEventName;
        this.map.get(key)?.forEach(cb => { try { cb(p.data, p); } catch { } });
        this.map.get("*")?.forEach(cb => { try { cb(p.data, p); } catch { } });
    }
    private scheduleReconnect() {
        if (this.dead || !this.opts.autoReconnect) return;

        // Evitar múltiples timers si ya hay uno programado
        if (this.timer) return;

        const { maxReconnectAttempts: max, reconnectDelay: base } = this.opts;
        if (max > 0 && this.attempts >= max) return;

        this.attempts++;
        const delay = base * Math.min(this.attempts, 6);

        this.timer = setTimeout(() => {
            this.timer = null;
            if (!this.dead) {
                this.connect().catch(() => { });
            }
        }, delay);
    }

    private startHeartbeat() {
        this.stopHeartbeat();
        this.heartbeatTimer = setInterval(() => {
            if (this.connected) {
                try { this.ws?.send(JSON.stringify({ event: "heartbeat", data: { timestamp: Date.now() } })); }
                catch { }
            }
        }, 30_000);
    }

    private stopHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }
}