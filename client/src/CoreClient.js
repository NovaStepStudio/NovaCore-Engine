'use strict';

const { EventEmitter } = require('events');
const WebSocket        = require('ws');

class CoreClient extends EventEmitter {
    constructor(opts = {}) {
        super();
        this.host = opts.host ?? 'localhost';
        this.httpPort = opts.httpPort ?? 7878;
        this.wsPort = opts.wsPort ?? 7879;
        this.baseUrl = `http://${this.host}:${this.httpPort}`;
        this.wsUrl = `ws://${this.host}:${this.wsPort}`;
        this._ws = null;
        this._ready = false;
    }
    
    connect() {
        return new Promise((resolve, reject) => {
            if (this._ready) { resolve(); return; }
            this._ws = new WebSocket(this.wsUrl);
            this._ws.on('open', () => {});
            this._ws.on('message', (raw) => {
                try {
                    const msg = JSON.parse(raw.toString());
                    this.emit(msg.event, msg.data);
                    this.emit('*', msg.event, msg.data);
                    if (msg.event === 'connected') { this._ready = true; resolve(); }
                } catch {}
            });
            this._ws.on('error', reject);
            this._ws.on('close', () => {
                this._ready = false;
                this.emit('ws:disconnected');
            });
        });
    }
    
    disconnect() {
        this._ws?.close();
        this._ws = null;
        this._ready = false;
    }
    
    apiInfo() { return this._get('/api'); }
    systemResources() { return this._get('/system/resources'); }
    versions(type = null) { return this._get(`/versions${type ? `?type=${type}` : ''}`); }
    progress(sessionId) { return this._get(`/progress?sessionId=${sessionId}`); }
    allSessions() { return this._get('/progress'); }
    launchStatus(id) { return this._get(`/launch/status/${id}`); }
    debugCategory(cat, sessionId = null) {
        return this._get(`/debug/download/${cat}${sessionId ? `?sessionId=${sessionId}` : ''}`);
    }
    
    install(opts) {
        return this._post('/install', {
            version: opts.version,
            instancePath:opts.instancePath,
            sharedPath: opts.sharedPath ?? null,
            download: opts.download ?? { client: true, libraries: true, assets: true, natives: true },
            verifySHA1: opts.verifySHA1 ?? true,
            maxThreads: opts.maxThreads ?? 0,
            debug: opts.debug ?? false,
        });
    }
    
    launch(opts) {
        return this._post('/launch', {
            version:              opts.version,
            instancePath:         opts.instancePath,
            sharedPath:           opts.sharedPath           ?? null,
            javaPath:             opts.javaPath             ?? null,
            hardwareAcceleration: opts.hardwareAcceleration ?? false,
            gcPreset:             opts.gcPreset             ?? 'auto',
            gpuPreference:        opts.gpuPreference        ?? 'auto',
            auth:                 opts.auth                 ?? null,
            authlibInjector:      opts.authlibInjector      ?? null,
            jvm:                  opts.jvm                  ?? null,
            window:               opts.window               ?? null,
            launcher:             opts.launcher             ?? null,
            features:             opts.features             ?? null,
            game:                 opts.game                 ?? null,
        });
    }
    
    killLaunch(launchId) { return this._post(`/launch/kill/${launchId}`, {}); }
    
    downloadRuntime(version, instancePath) {
        return this._post('/runtime/download', { version, instancePath });
    }
    
    listInstances()          { return this._get('/instances'); }
    getInstance(idOrName)    { return this._get(`/instances/${encodeURIComponent(idOrName)}`); }
    getInstancePath(idOrName){ return this._get(`/instances/${encodeURIComponent(idOrName)}/path`); }
    deleteInstance(idOrName) { return this._delete(`/instances/${encodeURIComponent(idOrName)}`); }
    
    updateInstance(idOrName, updates) {
        return this._patch(`/instances/${encodeURIComponent(idOrName)}`, updates);
    }
    
    createInstance(opts) {
        return this._post('/instances', {
            name:        opts.name,
            mcVersion:   opts.mcVersion,
            config:      opts.config      ?? null,
            autoInstall: opts.autoInstall ?? false,
            install:     opts.install     ?? null,
        });
    }
    
    waitForInstall(sessionId, onProgress = null) {
        return new Promise((resolve, reject) => {
            if (this._ready) {
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
                    this.off('session_progress', onProg);
                    this.off('session_completed', onDone);
                    this.off('session_failed', onFail);
                };
                this.on('session_progress', onProg);
                this.on('session_completed', onDone);
                this.on('session_failed', onFail);
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
                this.off('game_exited', listener);
                resolve(data);
            };
            this.on('game_exited', listener);
        });
    }
    
    onEvent(eventType, handler) {
        this.on(eventType, handler);
        return () => this.off(eventType, handler);
    }
    
    onGameLog(launchId, handler) {
        const listener = (data) => { if (data.launchId === launchId) handler(data.line); };
        this.on('game_log', listener);
        return () => this.off('game_log', listener);
    }
    
    async _get(path) {
        const res  = await fetch(this.baseUrl + path);
        const body = await res.json();
        if (!res.ok) throw new Error(`GET ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }
    
    async _post(path, data) {
        const res  = await fetch(this.baseUrl + path, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data),
        });
        const body = await res.json();
        if (!res.ok) throw new Error(`POST ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }
    
    async _patch(path, data) {
        const res  = await fetch(this.baseUrl + path, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data),
        });
        const body = await res.json();
        if (!res.ok) throw new Error(`PATCH ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }
    
    async _delete(path) {
        const res  = await fetch(this.baseUrl + path, { method: 'DELETE' });
        const body = await res.json();
        if (!res.ok) throw new Error(`DELETE ${path} → ${res.status}: ${JSON.stringify(body)}`);
        return body;
    }
}

module.exports = CoreClient;
