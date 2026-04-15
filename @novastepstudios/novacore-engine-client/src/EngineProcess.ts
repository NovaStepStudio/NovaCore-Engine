import { spawn, ChildProcess, execSync } from "node:child_process";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

export interface EngineProcessOptions {
    /**
    * Ruta al archivo JAR de novacore-engine.
    * @example "/opt/steplauncher/novacore-engine.jar"
    */
    jar: string;
    
    /**
    * Ejecutable de Java a usar.
    * @default "java" (debe estar en el PATH)
    */
    java?: string;
    
    /** Puerto HTTP. @default 7878 */
    httpPort?: number;
    
    /** Puerto WebSocket. @default 7879 */
    wsPort?: number;
    
    /**
    * Carpeta donde se guardan las instancias por defecto.
    */
    instancesDir?: string;
    
    /**
    * Carpeta donde se escriben los logs del motor.
    * @default `<instancesDir>/../logs`
    */
    logDir?: string;
    
    /**
    * Nivel de log del motor.
    * @default "INFO"
    */
    logLevel?: "DEBUG" | "INFO" | "WARN" | "ERROR";
    
    /**
    * Nombre del launcher para los logs (ayuda en debugging).
    * @default "StepLauncher"
    */
    launcherName?: string;
    
    /**
    * Hilos de descarga paralelos.
    * @default 32
    */
    threads?: number;
    
    /**
    * Milisegundos a esperar para que el motor diga `[Core] Ready`.
    * @default 15_000
    */
    startupTimeoutMs?: number;
    
    /**
    * Si es true, la salida del motor se redirige a la consola actual.
    * @default false
    */
    verbose?: boolean;
    
    /**
    * Argumentos extra para la JVM (ej: memoria).
    * @example ["-Xmx256m", "-XX:+UseG1GC"]
    */
    jvmArgs?: string[];
    
    /**
    * Si es true, el motor se cierra automáticamente cuando el proceso Node termina.
    * @default true
    */
    autoKillOnExit?: boolean;
}

export interface EngineProcessInfo {
    /** PID del proceso Java. */
    pid: number;
    /** Token de acceso (extraído de la consola). */
    token: string;
    /** URL HTTP del motor. */
    httpUrl: string;
    /** URL WebSocket del motor. */
    wsUrl: string;
}

/**
* Gestiona el ciclo de vida del proceso Java de NovaCore Engine.
* 
* Se encarga de iniciar el motor, extraer el token de seguridad y asegurar
* un cierre limpio de los procesos hijos (Minecraft).
*/
export class EngineProcess {
    private opts: Required<EngineProcessOptions>;
    private child: ChildProcess | null = null;
    private _info: EngineProcessInfo | null = null;
    private exitHandler: (() => void) | null = null;
    
    constructor(opts: EngineProcessOptions) {
        this.opts = {
            java:             "java",
            httpPort:         7878,
            wsPort:           7879,
            instancesDir:     resolve("instances"),
            logDir:           "",
            logLevel:         "INFO",
            launcherName:     "StepLauncher",
            threads:          32,
            startupTimeoutMs: 15_000,
            verbose:          false,
            jvmArgs:          [],
            autoKillOnExit:   true,
            ...opts,
        };
        
        if (!this.opts.logDir) {
            this.opts.logDir = resolve(this.opts.instancesDir, "..", "logs");
        }
    }
    
    /**
    * Inicia el motor y espera a que esté listo para recibir peticiones.
    */
    async start(): Promise<EngineProcessInfo> {
        if (this.child && !this.child.killed) {
            throw new Error("El motor ya está en ejecución.");
        }
        
        const { jar, java } = this.opts;
        
        if (!existsSync(jar)) {
            throw new Error(`No se encontró el JAR de novacore-engine: ${jar}`);
        }
        
        const args = [
            ...this.opts.jvmArgs,
            "-jar", jar,
            "--port",          String(this.opts.httpPort),
            "--ws-port",       String(this.opts.wsPort),
            "--threads",       String(this.opts.threads),
            "--instances-dir", this.opts.instancesDir,
            "--log-dir",       this.opts.logDir,
            "--log-level",     this.opts.logLevel,
            "--launcher-name", this.opts.launcherName,
        ];
        
        this.child = spawn(java, args, {
            stdio: ["ignore", "pipe", "pipe"],
            // En Windows, permitir que el proceso hijo se cree en un grupo separado para evitar huérfanos
            detached: process.platform !== "win32",
        });
        
        const info = await this.waitForReady();
        this._info = info;
        
        if (this.opts.verbose) {
            this.child.stdout?.on("data", (d: Buffer) => process.stdout.write(d));
            this.child.stderr?.on("data", (d: Buffer) => process.stderr.write(d));
        }
        
        if (this.opts.autoKillOnExit) {
            this.setupExitHandlers();
        }
        
        return info;
    }
    
    /**
    * Cierra el motor de forma segura intentando una parada elegante.
    */
    async stop(): Promise<void> {
        if (!this.child || this.child.killed) return;
        
        this.removeExitHandlers();

        // Intentar cierre elegante vía API si tenemos el token
        if (this._info) {
            try {
                const ctrl = new AbortController();
                const t = setTimeout(() => ctrl.abort(), 2000);
                await fetch(`${this._info.httpUrl}/close`, {
                    method: "POST",
                    headers: { "X-Access-Token": this._info.token },
                    signal: ctrl.signal
                });
                clearTimeout(t);
            } catch (e) {
                // Si la API falla, seguimos con el cierre forzoso
            }
        }
        
        return new Promise<void>((res) => {
            const timeout = setTimeout(() => { this.kill(); res(); }, 3000);
            this.child!.once("exit", () => { clearTimeout(timeout); res(); });
            
            // Si no cerró por la API, mandamos SIGTERM
            this.child!.kill("SIGTERM");
        });
    }
    
    /**
    * Cierra el proceso inmediatamente y mata a todos los descendientes (Minecraft).
    */
    kill(): void {
        this.removeExitHandlers();
        if (!this.child || this.child.killed) return;

        const pid = this.child.pid;
        if (pid) {
            try {
                if (process.platform === "win32") {
                    // taskkill /F /T mata todo el árbol de procesos en Windows
                    execSync(`taskkill /F /T /PID ${pid}`, { stdio: "ignore" });
                } else {
                    // En Unix intentamos matar el grupo de procesos si es detached
                    const pgid = -pid;
                    process.kill(pgid, "SIGKILL");
                }
            } catch (e) {
                // Fallback a kill simple si taskkill o pgid fallan
                this.child.kill("SIGKILL");
            }
        }

        this.child = null;
        this._info = null;
    }
    
    get running(): boolean { return !!this.child && !this.child.killed; }
    get info(): EngineProcessInfo | null { return this._info; }
    
    private waitForReady(): Promise<EngineProcessInfo> {
        return new Promise<EngineProcessInfo>((resolve, reject) => {
            const { httpPort, wsPort, startupTimeoutMs } = this.opts;
            const child = this.child!;
            
            let token    = "";
            let ready    = false;
            let buf      = "";
            
            const timeout = setTimeout(() => {
                cleanup();
                reject(new Error(
                    `El motor no inició en ${startupTimeoutMs}ms. Verifica Java y el archivo JAR.`
                ));
            }, startupTimeoutMs);
            
            const cleanup = () => {
                clearTimeout(timeout);
                child.stdout?.removeListener("data", onData);
                child.removeListener("error", onError);
                child.removeListener("exit", onExit);
            };
            
            const onData = (chunk: Buffer) => {
                buf += chunk.toString("utf8");
                const lines = buf.split("\n");
                buf = lines.pop() ?? "";
                
                for (const line of lines) {
                    const trimmed = line.trim();
                    if (!token && trimmed.startsWith("TOKEN:")) {
                        token = trimmed.slice(6).trim();
                    }
                    if (!ready && trimmed.includes("[Core] Ready") && token) {
                        ready = true;
                        cleanup();
                        resolve({
                            pid:     child.pid!,
                            token,
                            httpUrl: `http://localhost:${httpPort}`,
                            wsUrl:   `ws://localhost:${wsPort}`,
                        });
                    }
                }
            };
            
            const onError = (err: Error) => {
                cleanup();
                if ((err as any).code === "ENOENT") {
                    reject(new Error(`No se encontró Java en "${this.opts.java}".`));
                } else {
                    reject(err);
                }
            };
            
            const onExit = (code: number | null) => {
                cleanup();
                reject(new Error(`El proceso terminó inesperadamente (código ${code}) antes de estar listo.`));
            };
            
            child.stdout?.on("data", onData);
            child.once("error", onError);
            child.once("exit", onExit);
        });
    }
    
    private setupExitHandlers() {
        this.exitHandler = () => this.kill();
        
        // El evento "exit" es para cierres normales de Node
        process.once("exit",    this.exitHandler);
        
        // Señales de interrupción para cierres manuales (Ctrl+C, etc)
        process.once("SIGINT",  this.exitHandler);
        process.once("SIGTERM", this.exitHandler);
        process.once("SIGHUP",  this.exitHandler);

        // En Windows, SIGINT a veces no se captura bien en apps de escritorio, 
        // pero Node suele manejarlo si hay un listener.
    }

    private removeExitHandlers() {
        if (this.exitHandler) {
            process.off("exit",    this.exitHandler);
            process.off("SIGINT",  this.exitHandler);
            process.off("SIGTERM", this.exitHandler);
            process.off("SIGHUP",  this.exitHandler);
            this.exitHandler = null;
        }
    }
}