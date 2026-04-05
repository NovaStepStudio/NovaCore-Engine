'use strict';

const { spawn }       = require('child_process');
const { LOG_DIR } = require('./examples/config');
const { EventEmitter } = require('events');
const path            = require('path');

class CoreProcess extends EventEmitter {
    constructor(opts = {}) {
        super();

        this.jarPath      = opts.jarPath      ?? path.join(__dirname, '../../build/libs/novacore-engine.jar');
        this.javaPath     = opts.javaPath      ?? 'java';
        this.httpPort     = opts.httpPort      ?? 7878;
        this.wsPort       = opts.wsPort        ?? 7879;
        this.threads      = opts.threads       ?? 32;
        this.jvmArgs      = opts.jvmArgs       ?? ['-Xms32m', '-Xmx128m', '-XX:+UseG1GC'];
        this.verbose      = opts.verbose       ?? true;
        this.instancesDir = opts.instancesDir  ?? null;
        this.logDir       = opts.logDir        ?? LOG_DIR;
        this.launcherName = opts.launcherName  ?? null;
        this.logLevel     = opts.logLevel      ?? null;
        this.clearLogs    = opts.clearLogs     ?? false;

        this.accessToken = null;

        this._proc     = null;
        this._stopping = false;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    start() {
        return new Promise((resolve, reject) => {
            if (this._proc) return reject(new Error('CoreProcess is already running'));

            this._stopping   = false;
            this.accessToken = null;

            const args = [
                ...this.jvmArgs,
                '-jar', this.jarPath,
                '--port',     String(this.httpPort),
                '--ws-port',  String(this.wsPort),
                '--threads',  String(this.threads),
            ];

            if (this.instancesDir) args.push('--instances-dir', this.instancesDir);
            if (this.logDir)       args.push('--log-dir',       this.logDir);
            if (this.launcherName) args.push('--launcher-name', this.launcherName);
            if (this.logLevel)     args.push('--log-level',     this.logLevel);
            if (this.clearLogs)    args.push('--clear-logs',    'false');

            this._proc = spawn(this.javaPath, args, { stdio: ['ignore', 'pipe', 'pipe'] });

            let resolved = false;

            const startTimeout = setTimeout(() => {
                if (resolved) return;
                resolved = true;
                reject(new Error('CoreProcess did not start within 20 seconds'));
                this._killProc();
            }, 20_000);

            const onStdoutLine = (line) => {
                const trimmed = line.trim();

                // ── Token channel ─────────────────────────────────────────────
                if (trimmed.startsWith('TOKEN:')) {
                    this.accessToken = trimmed.slice('TOKEN:'.length).trim();
                    if (this.verbose) {
                        process.stdout.write('[CoreProcess] Access token intercepted\n');
                    }
                    return;
                }

                // ── Log channel ───────────────────────────────────────────────
                if (this.verbose) process.stdout.write(`[Java] ${trimmed}\n`);
                this.emit('log', trimmed);

                // Ready signal
                if (!resolved && trimmed.includes('[Core] Ready')) {
                    resolved = true;
                    clearTimeout(startTimeout);
                    this.emit('ready');
                    resolve();
                }
            };

            this._proc.stdout.on('data', (buf) =>
                buf.toString().split('\n').filter(Boolean).forEach(onStdoutLine)
            );

            // ── stderr handler ────────────────────────────────────────────────
            this._proc.stderr.on('data', (buf) => {
                buf.toString().split('\n').filter(Boolean).forEach((line) => {
                    this.emit('stderr', line);
                    if (this.verbose) process.stderr.write(`[Java:err] ${line}\n`);
                });
            });

            this._proc.on('error', (err) => {
                clearTimeout(startTimeout);
                if (!resolved) { resolved = true; reject(err); }
                this._proc = null;
                this.emit('error', err);
            });

            this._proc.on('exit', (code, signal) => {
                clearTimeout(startTimeout);
                this._proc        = null;
                this.accessToken  = null;
                const exitCode    = code ?? (signal ? 1 : 0);
                if (!resolved) {
                    resolved = true;
                    reject(new Error(
                        `CoreProcess exited before becoming ready (code=${exitCode}, signal=${signal})`));
                }
                this.emit('exit', exitCode);
            });
        });
    }

    stop() {
        return new Promise((resolve) => {
            if (!this._proc) { resolve(); return; }
            this._stopping = true;
            const proc = this._proc;
            proc.once('exit', () => resolve());
            this._killProc(proc);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    _killProc(proc = this._proc) {
        if (!proc) return;
        try { proc.kill('SIGTERM'); } catch (_) {}
        const fallback = setTimeout(() => {
            try { proc.kill('SIGKILL'); } catch (_) {}
        }, 3_000);
        if (fallback.unref) fallback.unref();
    }

    get running() { return this._proc != null && !this._proc.killed; }
    get pid()     { return this._proc?.pid; }
}

module.exports = CoreProcess;
