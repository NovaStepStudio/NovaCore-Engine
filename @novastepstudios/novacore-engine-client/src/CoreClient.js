'use strict';

const { EventEmitter } = require('events');
const http = require('http');
const WebSocket = require('ws');

const POLL_INTERVAL_MS = 600;
const WS_CONNECT_TIMEOUT_MS = 10_000;

/**
* CoreClient — cliente HTTP + WebSocket para NovaCore-Engine.
*
* Hereda de EventEmitter y re-emite todos los eventos del WebSocket
* con sus nombres originales. También emite el wildcard '*' con
* (eventName, data) para escuchar todo en un solo handler.
*/
class CoreClient extends EventEmitter {
    /**
    * @param {object} [opts]
    * @param {string} [opts.host]     Host del engine. Default: 'localhost'
    * @param {number} [opts.httpPort] Puerto HTTP. Default: 7878
    * @param {number} [opts.wsPort]   Puerto WebSocket. Default: 7879
    */
    constructor(opts = {}) {
        super();
        this._host     = opts.host     ?? 'localhost';
        this._httpPort = opts.httpPort ?? 7878;
        this._wsPort   = opts.wsPort   ?? 7879;
        
        /** @type {WebSocket | null} */
        this._ws = null;
        this._wsConnected = false;
    }
    
    /**
    * Conecta el WebSocket al engine y espera el evento 'connected'.
    * Si ya está conectado, resuelve inmediatamente.
    * @returns {Promise<void>}
    */
    connect() {
        if (this._wsConnected) return Promise.resolve();
        
        return new Promise((resolve, reject) => {
            const url = `ws://${this._host}:${this._wsPort}`;
            const ws  = new WebSocket(url);
            this._ws  = ws;
            
            const timeout = setTimeout(() => {
                ws.terminate();
                reject(new Error(`Timeout conectando al WebSocket de NovaCore-Engine (${url})`));
            }, WS_CONNECT_TIMEOUT_MS);
            
            ws.on('open', () => {
                // no resolvemos aún — esperamos el mensaje 'connected' del engine
            });
            
            ws.on('message', (raw) => {
                let msg;
                try { msg = JSON.parse(raw.toString()); } catch { return; }
                
                const { event, data } = msg;
                if (!event) return;
                
                // Resolver la promesa cuando llega 'connected'
                if (event === 'connected' && !this._wsConnected) {
                    this._wsConnected = true;
                    clearTimeout(timeout);
                    resolve();
                }
                
                // Re-emitir con nombre original
                this.emit(event, data);
                
                // Wildcard
                this.emit('*', event, data);
            });
            
            ws.on('close', () => {
                this._wsConnected = false;
                this._ws = null;
                this.emit('ws:disconnected');
            });
            
            ws.on('error', (err) => {
                clearTimeout(timeout);
                if (!this._wsConnected) {
                    reject(new Error(`Error de WebSocket: ${err.message}`));
                }
                this.emit('ws:error', err);
            });
        });
    }
    
    /** Cierra el WebSocket. */
    disconnect() {
        if (this._ws) {
            this._ws.close();
            this._ws = null;
            this._wsConnected = false;
        }
    }
    
    /**
    * @param {string} path
    * @returns {Promise<any>}
    */
    _get(path) {
        return new Promise((resolve, reject) => {
            const url = `http://${this._host}:${this._httpPort}${path}`;
            http.get(url, (res) => {
                let body = '';
                res.on('data', (c) => (body += c));
                res.on('end', () => {
                    try {
                        const json = JSON.parse(body);
                        if (res.statusCode >= 400) {
                            reject(new Error(`GET ${path} → ${res.statusCode}: ${json.error ?? body}`));
                        } else {
                            resolve(json);
                        }
                    } catch {
                        reject(new Error(`GET ${path} → respuesta no-JSON: ${body}`));
                    }
                });
            }).on('error', reject);
        });
    }
    
    /**
    * @param {string} path
    * @param {any}    body
    * @param {string} [method] Default: 'POST'
    * @returns {Promise<any>}
    */
    _request(path, body, method = 'POST') {
        return new Promise((resolve, reject) => {
            const payload = body !== undefined ? JSON.stringify(body) : '';
            const opts = {
                hostname: this._host,
                port:     this._httpPort,
                path,
                method,
                headers: {
                    'Content-Type':   'application/json',
                    'Content-Length': Buffer.byteLength(payload),
                },
            };
            
            const req = http.request(opts, (res) => {
                let raw = '';
                res.on('data', (c) => (raw += c));
                res.on('end', () => {
                    try {
                        const json = JSON.parse(raw);
                        if (res.statusCode >= 400) {
                            reject(new Error(`${method} ${path} → ${res.statusCode}: ${json.error ?? raw}`));
                        } else {
                            resolve(json);
                        }
                    } catch {
                        reject(new Error(`${method} ${path} → respuesta no-JSON: ${raw}`));
                    }
                });
            });
            
            req.on('error', reject);
            if (payload) req.write(payload);
            req.end();
        });
    }
    
    /** @returns {Promise<import('../types').ApiInfoResponse>} */
    apiInfo() {
        return this._get('/api');
    }
    
    /** @returns {Promise<import('../types').SystemResourcesResponse>} */
    systemResources() {
        return this._get('/system/resources');
    }
    
    /**
    * @param {'release'|'snapshot'|'old_alpha'|'old_beta'|null} [type]
    * @returns {Promise<import('../types').VersionsResponse>}
    */
    versions(type = null) {
        const qs = type ? `?type=${type}` : '';
        return this._get(`/versions${qs}`);
    }
    
    /**
    * Inicia la instalación de una versión de Minecraft.
    * @param {import('../types').InstallOptions} opts
    * @returns {Promise<import('../types').InstallResponse>}
    */
    install(opts) {
        return this._request('/install', opts);
    }
    
    /**
    * Consulta el estado de una sesión de descarga.
    * @param {string} sessionId
    * @returns {Promise<import('../types').SessionSnapshot>}
    */
    progress(sessionId) {
        return this._get(`/progress?sessionId=${encodeURIComponent(sessionId)}`);
    }
    
    /**
    * Lista todas las sesiones activas.
    * @returns {Promise<{ count: number; sessions: import('../types').SessionSnapshot[] }>}
    */
    allSessions() {
        return this._get('/progress');
    }
    
    /**
    * Espera a que una instalación termine, usando WebSocket si está conectado
    * o polling de lo contrario.
    * @param {string} sessionId
    * @param {(snap: import('../types').SessionSnapshot) => void} [onProgress]
    * @returns {Promise<import('../types').SessionSnapshot>}
    */
    waitForInstall(sessionId, onProgress) {
        return new Promise((resolve, reject) => {
            if (this._wsConnected) {
                const onCompleted = (data) => {
                    if (data.session !== sessionId) return;
                    cleanup();
                    this.progress(sessionId).then(resolve).catch(reject);
                };
                
                const onFailed = (data) => {
                    if (data.session !== sessionId) return;
                    cleanup();
                    this.progress(sessionId).then(resolve).catch(reject);
                };
                
                const onProgressEvt = (data) => {
                    if (data.session !== sessionId) return;
                    if (onProgress) {
                        // Convertir SessionProgress → SessionSnapshot parcial para el callback
                        this.progress(sessionId).then(onProgress).catch(() => {});
                    }
                };
                
                const cleanup = () => {
                    this.off('session_completed', onCompleted);
                    this.off('session_failed',    onFailed);
                    this.off('session_progress',  onProgressEvt);
                };
                
                this.on('session_completed', onCompleted);
                this.on('session_failed',    onFailed);
                if (onProgress) this.on('session_progress', onProgressEvt);
                
                // Safety: verificar si ya terminó antes de que llegara el evento
                this.progress(sessionId).then((snap) => {
                    if (snap.status === 'completed' || snap.status === 'failed') {
                        cleanup();
                        resolve(snap);
                    }
                }).catch(() => {});
            } else {
                const poll = async () => {
                    try {
                        const snap = await this.progress(sessionId);
                        if (onProgress) onProgress(snap);
                        if (snap.status === 'completed' || snap.status === 'failed') {
                            resolve(snap);
                        } else {
                            setTimeout(poll, POLL_INTERVAL_MS);
                        }
                    } catch (err) {
                        reject(err);
                    }
                };
                poll();
            }
        });
    }
    
    /**
    * Lanza Minecraft.
    * @param {import('../types').LaunchOptions} opts
    * @returns {Promise<import('../types').LaunchResponse>}
    */
    launch(opts) {
        return this._request('/launch', opts);
    }
    
    /**
    * Mata un proceso de Minecraft en ejecución.
    * @param {string} launchId
    * @returns {Promise<{ launchId: string; status: 'killed' }>}
    */
    killLaunch(launchId) {
        return this._request(`/launch/kill/${encodeURIComponent(launchId)}`, {});
    }
    
    /**
    * Consulta si un proceso sigue corriendo.
    * @param {string} launchId
    * @returns {Promise<{ launchId: string; running: boolean; status: string }>}
    */
    launchStatus(launchId) {
        return this._get(`/launch/status/${encodeURIComponent(launchId)}`);
    }
    
    /**
    * Espera a que el juego cierre.
    * @param {string} launchId
    * @returns {Promise<{ launchId: string; exitCode: number; status: 'clean'|'crash' }>}
    */
    waitForGame(launchId) {
        return new Promise((resolve, reject) => {
            if (this._wsConnected) {
                const onExited = (data) => {
                    if (data.launchId !== launchId) return;
                    this.off('game_exited', onExited);
                    resolve(data);
                };
                this.on('game_exited', onExited);
            } else {
                // Polling de launchStatus
                const poll = async () => {
                    try {
                        const status = await this.launchStatus(launchId);
                        if (!status.running) {
                            resolve({ launchId, exitCode: -1, status: 'clean' });
                        } else {
                            setTimeout(poll, POLL_INTERVAL_MS);
                        }
                    } catch (err) {
                        reject(err);
                    }
                };
                poll();
            }
        });
    }
    
    /**
    * Suscribe a los logs de un proceso de Minecraft específico.
    * Devuelve una función de cleanup para dejar de escuchar.
    * @param {string} launchId
    * @param {(line: string) => void} handler
    * @returns {() => void} cleanup function
    */
    onGameLog(launchId, handler) {
        const listener = (data) => {
            if (data.launchId === launchId) handler(data.line);
        };
        this.on('game_log', listener);
        return () => this.off('game_log', listener);
    }
    
    /**
    * @param {import('../types').CreateInstanceOptions} opts
    * @returns {Promise<import('../types').CreateInstanceResponse>}
    */
    createInstance(opts) {
        return this._request('/instances', opts);
    }
    
    /** @returns {Promise<{ count: number; instances: import('../types').InstanceInfo[] }>} */
    listInstances() {
        return this._get('/instances');
    }
    
    /**
    * @param {string} idOrName
    * @returns {Promise<import('../types').InstanceInfo>}
    */
    getInstance(idOrName) {
        return this._get(`/instances/${encodeURIComponent(idOrName)}`);
    }
    
    /**
    * @param {string} idOrName
    * @returns {Promise<{ id: string; path: string }>}
    */
    getInstancePath(idOrName) {
        return this._get(`/instances/${encodeURIComponent(idOrName)}/path`);
    }
    
    /**
    * @param {string} idOrName
    * @param {Partial<import('../types').InstanceConfig>} updates
    * @returns {Promise<{ updated: boolean; id: string }>}
    */
    updateInstance(idOrName, updates) {
        return this._request(`/instances/${encodeURIComponent(idOrName)}`, updates, 'PATCH');
    }
    
    /**
    * @param {string} idOrName
    * @returns {Promise<{ deleted: boolean; id: string }>}
    */
    deleteInstance(idOrName) {
        return this._request(`/instances/${encodeURIComponent(idOrName)}`, undefined, 'DELETE');
    }
    
    /**
    * Descarga el runtime de Java de Mojang para una versión.
    * @param {string} version
    * @param {string} instancePath
    */
    downloadRuntime(version, instancePath) {
        return this._request('/runtime/download', { version, instancePath });
    }
    
    /**
    * @param {import('../types').FileCategory} category
    * @param {string|null} [sessionId]
    * @returns {Promise<import('../types').DebugResponse>}
    */
    debugCategory(category, sessionId = null) {
        const qs = sessionId ? `?sessionId=${encodeURIComponent(sessionId)}` : '';
        return this._get(`/debug/download/${category}${qs}`);
    }
    
    /**
    * Suscribe a un evento tipado. Devuelve cleanup function.
    * @template {keyof import('../types').CoreEvents} K
    * @param {K} event
    * @param {(data: import('../types').CoreEvents[K]) => void} handler
    * @returns {() => void}
    */
    onEvent(event, handler) {
        this.on(event, handler);
        return () => this.off(event, handler);
    }
}

module.exports = CoreClient;
