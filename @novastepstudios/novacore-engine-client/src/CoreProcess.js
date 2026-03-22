'use strict';

const { EventEmitter } = require('events');
const { spawn } = require('child_process');
const path = require('path');

const DEFAULT_JAR_PATH = path.resolve(__dirname, './libs/novacore-engine.jar');
const READY_SIGNAL = 'Ready';
const START_TIMEOUT_MS = 20_000;
const STOP_SIGKILL_WAIT_MS = 3_000;
const DEFAULT_JVM_ARGS = ['-Xms32m', '-Xmx128m', '-XX:+UseG1GC'];

/**
* CoreProcess — gestiona el ciclo de vida del proceso Java de NovaCore-Engine.
*
* Eventos emitidos:
*   'ready'  — el engine imprimió "Ready" y está listo para recibir requests
*   'log'    — línea de stdout del engine
*   'stderr' — línea de stderr del proceso Java
*   'exit'   — el proceso cerró, recibe el exit code
*/
class CoreProcess extends EventEmitter {
    /**
    * @param {object} [opts]
    * @param {string}   [opts.jarPath]      Ruta al JAR del engine. Default: ../../core/build/libs/novacore-engine.jar
    * @param {string}   [opts.javaPath]     Ejecutable Java. Default: 'java'
    * @param {number}   [opts.httpPort]     Puerto HTTP. Default: 7878
    * @param {number}   [opts.wsPort]       Puerto WebSocket. Default: 7879
    * @param {number}   [opts.threads]      Threads de descarga. 0 = auto. Default: 32
    * @param {string[]} [opts.jvmArgs]      Args JVM para el engine. Default: ['-Xms32m', '-Xmx128m', '-XX:+UseG1GC']
    * @param {boolean}  [opts.verbose]      Si true, imprime todo el stdout del engine. Default: false
    * @param {string}   [opts.instancesDir] Directorio de instancias. Default: null (usa ./instances)
    * @param {string}   [opts.logDir]       Directorio de logs. Default: null (usa ../logs)
    * @param {string}   [opts.launcherName] Nombre del launcher. Default: null
    * @param {string}   [opts.logLevel]     Nivel de log: 'DEBUG'|'INFO'|'WARN'|'ERROR'. Default: null
    */
    constructor(opts = {}) {
        super();
        
        this._jarPath      = opts.jarPath      ?? DEFAULT_JAR_PATH;
        this._javaPath     = opts.javaPath     ?? 'java';
        this._httpPort     = opts.httpPort     ?? 7878;
        this._wsPort       = opts.wsPort       ?? 7879;
        this._threads      = opts.threads      ?? 32;
        this._jvmArgs      = opts.jvmArgs      ?? DEFAULT_JVM_ARGS;
        this._verbose      = opts.verbose      ?? false;
        this._instancesDir = opts.instancesDir ?? null;
        this._logDir       = opts.logDir       ?? null;
        this._launcherName = opts.launcherName ?? null;
        this._logLevel     = opts.logLevel     ?? null;
        
        /** @type {import('child_process').ChildProcess | null} */
        this._process = null;
    }
    
    /**
    * Spawnea el proceso Java y espera a que esté listo.
    * Rechaza si no arranca en START_TIMEOUT_MS milisegundos.
    * @returns {Promise<void>}
    */
    start() {
        if (this._process?.exitCode === null && this._process.pid) {
            return Promise.resolve(); // ya está corriendo
        }
        
        return new Promise((resolve, reject) => {
            const args = [
                ...this._jvmArgs,
                '-jar', this._jarPath,
                '--port',     String(this._httpPort),
                '--ws-port',  String(this._wsPort),
                '--threads',  String(this._threads),
            ];
            
            if (this._instancesDir) args.push('--instances-dir', this._instancesDir);
            if (this._logDir)       args.push('--log-dir',       this._logDir);
            if (this._launcherName) args.push('--launcher-name', this._launcherName);
            if (this._logLevel)     args.push('--log-level',     this._logLevel);
            
            const proc = spawn(this._javaPath, args, { stdio: ['ignore', 'pipe', 'pipe'] });
            this._process = proc;
            
            let resolved = false;
            
            const timeout = setTimeout(() => {
                if (!resolved) {
                    resolved = true;
                    proc.kill();
                    reject(new Error(`NovaCore-Engine no arrancó en ${START_TIMEOUT_MS / 1000} segundos`));
                }
            }, START_TIMEOUT_MS);
            
            let stdoutBuf = '';
            proc.stdout.on('data', (chunk) => {
                stdoutBuf += chunk.toString();
                const lines = stdoutBuf.split('\n');
                stdoutBuf = lines.pop(); // la última línea puede estar incompleta
                
                for (const line of lines) {
                    const trimmed = line.trim();
                    if (!trimmed) continue;
                    
                    if (this._verbose) this.emit('log', trimmed);
                    
                    if (!resolved && trimmed.includes(READY_SIGNAL)) {
                        resolved = true;
                        clearTimeout(timeout);
                        this.emit('ready');
                        resolve();
                    }
                }
            });
            
            let stderrBuf = '';
            proc.stderr.on('data', (chunk) => {
                stderrBuf += chunk.toString();
                const lines = stderrBuf.split('\n');
                stderrBuf = lines.pop();
                
                for (const line of lines) {
                    const trimmed = line.trim();
                    if (trimmed) this.emit('stderr', trimmed);
                }
            });
            
            proc.on('exit', (code) => {
                this._process = null;
                clearTimeout(timeout);
                
                if (!resolved) {
                    resolved = true;
                    reject(new Error(`El proceso Java salió con código ${code} antes de estar listo`));
                }
                
                this.emit('exit', code ?? -1);
            });
            
            proc.on('error', (err) => {
                clearTimeout(timeout);
                if (!resolved) {
                    resolved = true;
                    reject(new Error(`No se pudo spawnear el proceso Java: ${err.message}`));
                }
            });
        });
    }
    
    /**
    * Detiene el engine. Envía SIGTERM y si no cierra en STOP_SIGKILL_WAIT_MS, SIGKILL.
    * @returns {Promise<void>}
    */
    stop() {
        const proc = this._process;
        if (!proc || proc.exitCode !== null) return Promise.resolve();
        
        return new Promise((resolve) => {
            const timer = setTimeout(() => {
                if (proc.exitCode === null) proc.kill('SIGKILL');
                resolve();
            }, STOP_SIGKILL_WAIT_MS);
            
            proc.once('exit', () => {
                clearTimeout(timer);
                resolve();
            });
            
            proc.kill('SIGTERM');
        });
    }
    
    /** true si el proceso está vivo */
    get running() {
        return this._process !== null && this._process.exitCode === null;
    }
    
    /** PID del proceso Java, o undefined si no está corriendo */
    get pid() {
        return this._process?.pid;
    }
}

module.exports = CoreProcess;
