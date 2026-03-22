'use strict';

const { spawn } = require('child_process');
const { EventEmitter } = require('events');
const path = require('path');

class CoreProcess extends EventEmitter {
    constructor(opts = {}) {
        super();
        this.jarPath = opts.jarPath ?? path.join(__dirname, '../../build/libs/novacore-engine.jar');
        this.javaPath = opts.javaPath ?? 'java';
        this.httpPort = opts.httpPort ?? 7878;
        this.wsPort = opts.wsPort ?? 7879;
        this.threads = opts.threads ?? 32;
        this.jvmArgs = opts.jvmArgs ?? ['-Xms32m', '-Xmx128m', '-XX:+UseG1GC'];
        this.verbose = opts.verbose ?? false;
        this.instancesDir = opts.instancesDir ?? null;
        this.logDir = opts.logDir ?? null;
        this.launcherName = opts.launcherName ?? null;
        this.logLevel = opts.logLevel ?? null;
        this._proc = null;
    }
    
    start() {
        return new Promise((resolve, reject) => {
            const args = [
                ...this.jvmArgs,
                '-jar', this.jarPath,
                '--port', String(this.httpPort),
                '--ws-port', String(this.wsPort),
                '--threads', String(this.threads),
            ];
            
            if (this.instancesDir) args.push('--instances-dir', this.instancesDir);
            if (this.logDir) args.push('--log-dir', this.logDir);
            if (this.launcherName) args.push('--launcher-name', this.launcherName);
            if (this.logLevel) args.push('--log-level', this.logLevel);
            
            this._proc = spawn(this.javaPath, args, { stdio: ['ignore', 'pipe', 'pipe'] });
            
            const timeout = setTimeout(() => {
                reject(new Error('Core no arrancó en 20 segundos'));
                this.stop();
            }, 20_000);
            
            const onLine = (line) => {
                if (this.verbose) process.stdout.write(`[Core] ${line}\n`);
                this.emit('log', line);
                if (line.includes('Ready')) {
                    clearTimeout(timeout);
                    this.emit('ready');
                    resolve();
                }
            };
            
            this._proc.stdout.on('data', (buf) =>
                buf.toString().split('\n').filter(Boolean).forEach(onLine)
        );
        
        this._proc.stderr.on('data', (buf) => {
            buf.toString().split('\n').filter(Boolean).forEach((line) => {
                this.emit('stderr', line);
                if (process.env.VERBOSE) process.stderr.write('[Java] ' + line + '\n');
            });
        });
        
        this._proc.on('error', (err) => { clearTimeout(timeout); reject(err); });
        this._proc.on('exit', (code) => {
            clearTimeout(timeout);
            this._proc = null;
            this.emit('exit', code);
        });
    });
}

stop() {
    return new Promise((resolve) => {
        if (!this._proc) { resolve(); return; }
        this._proc.once('exit', resolve);
        this._proc.kill('SIGTERM');
        setTimeout(() => this._proc?.kill('SIGKILL'), 3000);
    });
}

get running() { return this._proc != null && !this._proc.killed; }
get pid() { return this._proc?.pid; }
}

module.exports = CoreProcess;
