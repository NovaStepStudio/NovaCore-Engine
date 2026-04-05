'use strict';

const { EventEmitter } = require('events');
const { createHash }   = require('crypto');
const WebSocket        = require('ws');

// ─── Connectivity
const CONNECTIVITY_ENDPOINTS   = ['https://www.gstatic.com/generate_204', 'https://launchermeta.mojang.com'];
const CONNECTIVITY_TIMEOUT_MS  = 4_000;
const CONNECTIVITY_INTERVAL_MS = 30_000;

// ─── WebSocket reconnect
const WS_RECONNECT_BASE_MS  = 1_000;   // primer reintento
const WS_RECONNECT_MAX_MS   = 30_000;  // tope máximo
const WS_RECONNECT_FACTOR   = 2;       // backoff exponencial
const WS_RECONNECT_JITTER   = 0.2;     // ±20 % de ruido para evitar thundering herd
const WS_HEARTBEAT_MS       = 25_000;  // cada cuánto se envía ping
const WS_PONG_TIMEOUT_MS    = 5_000;   // tiempo máximo para recibir pong

// ─── Estado de la conexión
const STATE = Object.freeze({
    DISCONNECTED:  'disconnected',
    CONNECTING:    'connecting',
    CONNECTED:     'connected',
    RECONNECTING:  'reconnecting',
});

class CoreClient extends EventEmitter {

    constructor(opts = {}) {
        super();
        if (!opts.accessToken) throw new Error('CoreClient: accessToken is required');

        this.host        = opts.host      ?? 'localhost';
        this.httpPort    = opts.httpPort  ?? 7878;
        this.wsPort      = opts.wsPort   ?? 7879;
        this.accessToken = opts.accessToken;

        this.maxReconnects = opts.maxReconnects ?? 0;

        this.baseUrl = `http://${this.host}:${this.httpPort}`;
        this.wsUrl   = `ws://${this.host}:${this.wsPort}`;

        // ── Estado interno ────────────────────────────────────────────────────
        this._ws               = null;
        this._wsState          = STATE.DISCONNECTED;
        this._reconnectAttempt = 0;
        this._reconnectTimer   = null;
        this._heartbeatTimer   = null;
        this._pongTimer        = null;
        this._pongReceived     = false;

        // Primera promesa de connect() — se resuelve UNA sola vez
        this._connectResolve = null;
        this._connectReject  = null;
        this._firstConnect   = false;

        // ── Conectividad de red ───────────────────────────────────────────────
        this._online            = null;
        this._connectivityTimer = null;
    }

    /** Estado actual de la conexión WS. */
    get state() { return this._wsState; }

    /** true si la sesión WS está activa y autenticada. */
    get connected() { return this._wsState === STATE.CONNECTED; }

    /**
     * Abre la conexión WS. Resuelve cuando el servidor emite 'connected'.
     * Si la conexión cae, se reconecta automáticamente sin necesidad de volver
     * a llamar connect().
     */
    connect() {
        return new Promise((resolve, reject) => {
            if (this._wsState === STATE.CONNECTED) { resolve(); return; }

            this._connectResolve = resolve;
            this._connectReject  = reject;
            this._firstConnect   = false;

            this._openSocket();
        });
    }

    /** Cierra la conexión y cancela toda reconexión pendiente. */
    disconnect() {
        this._log('Desconexión solicitada por el usuario');
        this._cancelReconnect();
        this._stopHeartbeat();
        this.stopConnectivityMonitor();
        this._setState(STATE.DISCONNECTED);
        this._closeSocket(1000, 'Client disconnect');
        this._ws = null;
    }

    // ── Conectividad de red

    async isOnline() {
        const prev   = this._online;
        this._online = await this._checkConnectivity();
        if (prev !== null && prev !== this._online) {
            this.emit('connectivity:change', this._online);
        }
        return this._online;
    }

    startConnectivityMonitor(intervalMs = CONNECTIVITY_INTERVAL_MS) {
        if (this._connectivityTimer) return;
        this.isOnline();
        this._connectivityTimer = setInterval(() => this.isOnline(), intervalMs);
    }

    stopConnectivityMonitor() {
        if (!this._connectivityTimer) return;
        clearInterval(this._connectivityTimer);
        this._connectivityTimer = null;
    }

    // ── Internos: WebSocket

    _openSocket() {
        this._setState(
            this._reconnectAttempt > 0 ? STATE.RECONNECTING : STATE.CONNECTING
        );

        const url = `${this.wsUrl}?token=${encodeURIComponent(this.accessToken)}`;
        let ws;
        try {
            ws = new WebSocket(url, { headers: { 'X-Access-Token': this.accessToken } });
        } catch (err) {
            this._log(`Error al crear WebSocket: ${err.message}`, 'error');
            this._scheduleReconnect();
            return;
        }

        this._ws = ws;

        ws.on('open', () => {
            // La autenticación se confirma con el evento 'connected' del servidor,
            // no en este callback. No hacemos nada aquí todavía.
        });

        ws.on('pong', () => {
            this._pongReceived = true;
        });

        ws.on('message', (raw) => {
            try {
                const msg = JSON.parse(raw.toString());
                this.emit(msg.event, msg.data);
                this.emit('*', msg.event, msg.data);

                if (msg.event === 'connected') {
                    this._onAuthenticated();
                }
            } catch { /* JSON inválido — ignorar */ }
        });

        ws.on('error', (err) => {
            // 'error' siempre va seguido de 'close'; el manejo real va allá.
            this._log(`WS error: ${err.message}`, 'warn');
        });

        ws.on('close', (code, reason) => {
            const r = reason?.toString() || 'sin razón';
            this._log(`WS cerrado: code=${code} reason=${r}`, 'warn');
            this._stopHeartbeat();
            this._ws = null;

            if (this._wsState !== STATE.DISCONNECTED) {
                this.emit('ws:disconnected', { code, reason: r });
                this._scheduleReconnect();
            }
        });
    }

    _onAuthenticated() {
        this._setState(STATE.CONNECTED);
        this._reconnectAttempt = 0;
        this._startHeartbeat();

        this.emit('ws:connected');

        // Resolver la promesa de connect() solo la primera vez
        if (!this._firstConnect && this._connectResolve) {
            this._firstConnect = true;
            this._connectResolve();
            this._connectResolve = null;
            this._connectReject  = null;
        }
    }

    /** Programa el siguiente intento de reconexión con backoff + jitter. */
    _scheduleReconnect() {
        if (this._wsState === STATE.DISCONNECTED) return; // usuario pidió disconnect()

        if (this.maxReconnects > 0 && this._reconnectAttempt >= this.maxReconnects) {
            this._log(`Límite de reconexiones alcanzado (${this._reconnectAttempt})`, 'error');
            this._setState(STATE.DISCONNECTED);
            this.emit('ws:reconnect_failed', { attempts: this._reconnectAttempt });
            if (this._connectReject) {
                this._connectReject(new Error(`WS: falló tras ${this._reconnectAttempt} intentos`));
                this._connectResolve = null;
                this._connectReject  = null;
            }
            return;
        }

        const base  = Math.min(WS_RECONNECT_BASE_MS * WS_RECONNECT_FACTOR ** this._reconnectAttempt, WS_RECONNECT_MAX_MS);
        const jitter = base * WS_RECONNECT_JITTER * (Math.random() * 2 - 1); // ±20 %
        const delay = Math.round(base + jitter);

        this._reconnectAttempt++;
        this._setState(STATE.RECONNECTING);
        this._log(`Reconectando en ${delay} ms (intento #${this._reconnectAttempt})`, 'warn');
        this.emit('ws:reconnecting', { attempt: this._reconnectAttempt, delayMs: delay });

        this._reconnectTimer = setTimeout(() => {
            this._reconnectTimer = null;
            if (this._wsState !== STATE.DISCONNECTED) {
                this._openSocket();
            }
        }, delay);
    }

    _cancelReconnect() {
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }
        this._reconnectAttempt = 0;
    }

    _closeSocket(code = 1000, reason = '') {
        if (!this._ws) return;
        try { this._ws.close(code, reason); } catch { /* ya cerrado */ }
    }

    // ── Internos: Heartbeat ───────────────────────────────────────────────────

    /**
     * Heartbeat con ping/pong a nivel de frames WebSocket.
     * Si el servidor no responde en WS_PONG_TIMEOUT_MS ms, la conexión se
     * considera muerta y se termina forzosamente (trigger reconexión).
     */
    _startHeartbeat() {
        this._stopHeartbeat();
        this._heartbeatTimer = setInterval(() => {
            if (!this._ws || this._ws.readyState !== WebSocket.OPEN) return;

            this._pongReceived = false;
            try { this._ws.ping(); } catch { return; }

            this._pongTimer = setTimeout(() => {
                if (!this._pongReceived && this._wsState === STATE.CONNECTED) {
                    this._log('Heartbeat timeout — conexión muerta, terminando...', 'warn');
                    try { this._ws?.terminate(); } catch { /* ignorar */ }
                }
            }, WS_PONG_TIMEOUT_MS);

        }, WS_HEARTBEAT_MS);
    }

    _stopHeartbeat() {
        if (this._heartbeatTimer) { clearInterval(this._heartbeatTimer); this._heartbeatTimer = null; }
        if (this._pongTimer)      { clearTimeout(this._pongTimer);       this._pongTimer      = null; }
    }

    // ── Internos: State machine

    _setState(newState) {
        if (this._wsState === newState) return;
        const prev = this._wsState;
        this._wsState = newState;
        this._log(`Estado WS: ${prev} → ${newState}`);
        this.emit('ws:state', newState, prev);
    }

    _log(msg, level = 'info') {
        const prefix = `[CoreClient]`;
        if (level === 'error') console.error(`${prefix} ${msg}`);
        else if (level === 'warn') console.warn(`${prefix} ${msg}`);
        else console.log(`${prefix} ${msg}`);
    }

    // ── Conectividad de red

    async _checkConnectivity() {
        for (const url of CONNECTIVITY_ENDPOINTS) {
            try {
                const ctrl  = new AbortController();
                const timer = setTimeout(() => ctrl.abort(), CONNECTIVITY_TIMEOUT_MS);
                await fetch(url, { method: 'HEAD', signal: ctrl.signal });
                clearTimeout(timer);
                return true;
            } catch { }
        }
        return false;
    }

    // ── HTTP API

    apiInfo()              { return this._get('/api'); }
    systemResources()      { return this._get('/system/resources'); }
    versions(type = null)  { return this._get(`/versions${type ? `?type=${type}` : ''}`); }
    progress(sessionId)    { return this._get(`/progress?sessionId=${sessionId}`); }
    allSessions()          { return this._get('/progress'); }
    launchStatus(id)       { return this._get(`/launch/status/${id}`); }
    debugCategory(cat, sid){ return this._get(`/debug/download/${cat}${sid ? `?sessionId=${sid}` : ''}`); }

    async install(opts) {
        const online    = this._online ?? await this.isOnline();
        const dlFlags   = opts.download ?? { client: true, libraries: true, assets: true, natives: true };
        const effective = online ? dlFlags : { client: false, libraries: false, assets: false, natives: false };
        if (!online) this.emit('offline:install', { version: opts.version });

        return this._post('/install', {
            version:          opts.version,
            instancePath:     opts.instancePath,
            sharedPath:       opts.sharedPath       ?? null,
            download:         effective,
            verifySHA1:       opts.verifySHA1        ?? true,
            maxThreads:       opts.maxThreads        ?? 0,
            debug:            opts.debug             ?? false,
            modloader:        opts.modloader         ?? null,
            modloaderVersion: opts.modloaderVersion  ?? null,
        });
    }

    async launch(opts) {
        const online = this._online ?? await this.isOnline();
        let auth = opts.auth ?? null;

        if (!online && !auth) {
            const username = opts.auth?.username ?? 'Player';
            auth = { type: 'offline', username, uuid: this._offlineUUID(username), accessToken: 'offline', userType: 'offline' };
            this.emit('offline:launch', { username });
        }

        return this._post('/launch', {
            version:              opts.version,
            instancePath:         opts.instancePath,
            sharedPath:           opts.sharedPath            ?? null,
            javaPath:             opts.javaPath              ?? null,
            hardwareAcceleration: opts.hardwareAcceleration  ?? false,
            gcPreset:             opts.gcPreset              ?? 'auto',
            gpuPreference:        opts.gpuPreference         ?? 'auto',
            auth,
            authlibInjector:      opts.authlibInjector       ?? null,
            jvm:                  opts.jvm                   ?? null,
            window:               opts.window                ?? null,
            launcher:             opts.launcher              ?? null,
            features:             opts.features              ?? null,
            game:                 opts.game                  ?? null,
        });
    }

    killLaunch(launchId)   { return this._post(`/launch/kill/${launchId}`, {}); }

    downloadRuntime(versionOrOpts, instancePath, sharedPath) {
        let version, iPath, sPath;
        if (typeof versionOrOpts === 'object' && versionOrOpts !== null) {
            ({ version, instancePath: iPath, sharedPath: sPath = null } = versionOrOpts);
        } else {
            version = versionOrOpts; iPath = instancePath; sPath = sharedPath ?? null;
        }
        return this._post('/runtime', { version, instancePath: iPath, sharedPath: sPath });
    }

    listInstances()           { return this._get('/instances'); }
    getInstance(idOrName)     { return this._get(`/instances/${encodeURIComponent(idOrName)}`); }
    getInstancePath(idOrName) { return this._get(`/instances/${encodeURIComponent(idOrName)}/path`); }
    deleteInstance(idOrName)  { return this._delete(`/instances/${encodeURIComponent(idOrName)}`); }

    updateInstance(idOrName, updates) {
        return this._patch(`/instances/${encodeURIComponent(idOrName)}`, updates);
    }

    createInstance(opts) {
        return this._post('/instances', {
            name: opts.name, mcVersion: opts.mcVersion,
            config: opts.config ?? null, autoInstall: opts.autoInstall ?? false, install: opts.install ?? null,
        });
    }

    waitForInstall(sessionId, onProgress = null) {
        return new Promise((resolve, reject) => {
            if (this.connected) {
                const onProg = (data) => {
                    if (data.session !== sessionId && data.sessionId !== sessionId) return;
                    onProgress?.({ overallPercent: data.percent, ...data });
                };
                const onDone = (data) => {
                    if (data.session !== sessionId && data.sessionId !== sessionId) return;
                    cleanup();
                    this.progress(sessionId).then(resolve).catch(reject);
                };
                const onFail = (data) => {
                    if (data.session !== sessionId && data.sessionId !== sessionId) return;
                    cleanup();
                    reject(new Error(data.reason ?? data.detail?.reason ?? 'Install failed'));
                };
                const cleanup = () => {
                    this.off('session_progress',  onProg);
                    this.off('session_completed', onDone);
                    this.off('session_failed',    onFail);
                };
                this.on('session_progress',  onProg);
                this.on('session_completed', onDone);
                this.on('session_failed',    onFail);
            } else {
                const timer = setInterval(async () => {
                    const snap = await this.progress(sessionId).catch(() => null);
                    if (!snap) return;
                    onProgress?.(snap);
                    if (snap.status === 'completed') { clearInterval(timer); resolve(snap); }
                    if (snap.status === 'failed')    { clearInterval(timer); reject(new Error(snap.error ?? 'Failed')); }
                }, 600);
            }
        });
    }

    waitForGame(launchId) {
        return new Promise((resolve) => {
            const listener = (data) => {
                if (data.launchId !== launchId) return;
                this.off('launch_exited', listener);
                resolve(data);
            };
            this.on('launch_exited', listener);
        });
    }

    onEvent(eventType, handler)  { this.on(eventType, handler); return () => this.off(eventType, handler); }

    onGameLog(launchId, handler) {
        const listener = (data) => { if (data.launchId === launchId) handler(data.line); };
        this.on('game_log', listener);
        return () => this.off('game_log', listener);
    }

    listModLoaders()                         { return this._get('/modloaders'); }
    getModLoaderVersions(loaderName, mcVer)  { return this._get(`/modloaders/versions/${encodeURIComponent(loaderName)}/${encodeURIComponent(mcVer)}`); }
    getModLoaderState(instancePath)          { return this._get(`/modloaders/state/${encodeURIComponent(instancePath)}`); }
    deleteModLoaderState(instancePath)       { return this._delete(`/modloaders/state/${encodeURIComponent(instancePath)}`); }

    installModLoader(opts) {
        return this._post('/modloaders/install', {
            loader:           opts.loader,
            loaderVersion:    opts.loaderVersion  ?? null,
            minecraftVersion: opts.minecraftVersion,
            instancePath:     opts.instancePath,
            sharedPath:       opts.sharedPath      ?? null,
            maxThreads:       opts.maxThreads      ?? 0,
            debug:            opts.debug           ?? false,
        });
    }

    waitForModLoader(sessionId, onProgress = null) {
        return new Promise((resolve, reject) => {
            let timeoutHandle;
            const onDownload = (data) => {
                if (data.sessionId !== sessionId) return;
                onProgress?.({ loader: data.loader, files: data.files });
            };
            const onDone = (data) => { if (data.sessionId !== sessionId) return; cleanup(); resolve(data); };
            const onFail = (data) => {
                if (data.sessionId !== sessionId && data.session !== sessionId) return;
                cleanup();
                reject(new Error(data.reason ?? data.detail?.reason ?? 'Modloader install failed'));
            };
            const cleanup = () => {
                clearTimeout(timeoutHandle);
                this.off('modloader_downloading', onDownload);
                this.off('modloader_installed',   onDone);
                this.off('session_failed',        onFail);
            };
            this.on('modloader_downloading', onDownload);
            this.on('modloader_installed',   onDone);
            this.on('session_failed',        onFail);
            timeoutHandle = setTimeout(() => { cleanup(); reject(new Error(`waitForModLoader: session ${sessionId} timed out`)); }, 300_000);
        });
    }

    // ── HTTP interno

    _authHeaders() { return { 'X-Access-Token': this.accessToken, 'Content-Type': 'application/json' }; }

    async _get(path) {
        const res  = await fetch(this.baseUrl + path, { headers: this._authHeaders() });
        const body = await res.json();
        if (!res.ok) throw new Error(`GET ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }

    async _post(path, data) {
        const res  = await fetch(this.baseUrl + path, { method: 'POST', headers: this._authHeaders(), body: JSON.stringify(data) });
        const body = await res.json();
        if (!res.ok) throw new Error(`POST ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }

    async _patch(path, data) {
        const res  = await fetch(this.baseUrl + path, { method: 'PATCH', headers: this._authHeaders(), body: JSON.stringify(data) });
        const body = await res.json();
        if (!res.ok) throw new Error(`PATCH ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }

    async _delete(path) {
        const res  = await fetch(this.baseUrl + path, { method: 'DELETE', headers: this._authHeaders() });
        const body = await res.json();
        if (!res.ok) throw new Error(`DELETE ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }

    _offlineUUID(username) {
        const bytes = [...createHash('md5').update(`OfflinePlayer:${username}`).digest()];
        bytes[6] = (bytes[6] & 0x0f) | 0x30;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const h = bytes.map(b => b.toString(16).padStart(2, '0')).join('');
        return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;
    }
}

module.exports = { CoreClient, WS_STATE: STATE };
