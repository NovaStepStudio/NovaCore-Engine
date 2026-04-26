import { EngineProcess } from "./EngineProcess.js";
import { NovaCoreClient } from "./NovaCoreClient.js";
import type { EngineProcessOptions } from "./EngineProcess.js";
import type { NovaCoreClientOptions } from "./NovaCoreClient.js";

export type NovaCoreEngineOptions = EngineProcessOptions & {
    /**
    * Opciones extra para el cliente WebSocket/HTTP subyacente.
    */
    client?: Pick<NovaCoreClientOptions, "timeoutMs" | "autoReconnect">;
};

/**
* `NovaCoreEngine` es el punto de entrada principal para integrar el motor.
* 
* Proporciona métodos estáticos para iniciar el proceso Java, conectar el cliente
* y obtener una instancia de {@link NovaCoreClient} lista para usar en un solo paso.
*/
export class NovaCoreEngine {
    /**
    * Inicia el proceso Java, espera a que esté listo y retorna un {@link NovaCoreClient} conectado.
    * 
    * El proceso del motor se cerrará automáticamente cuando termine el proceso Node.
    * 
    * @throws Si el JAR no se encuentra, Java no está instalado o el inicio expira.
    */
    static async start(opts: NovaCoreEngineOptions): Promise<NovaCoreClient> {
        const proc = new EngineProcess(opts);
        const info = await proc.start();

        const client = new NovaCoreClient({
            httpUrl: info.httpUrl,
            wsUrl: info.wsUrl,
            token: info.token,
            timeoutMs: opts.client?.timeoutMs,
            autoReconnect: opts.client?.autoReconnect,
        });

        await client.connect();
        return client;
    }

    /**
    * Similar a `start`, pero retorna también el manipulador del proceso (`EngineProcess`).
    * 
    * Útil cuando necesitas control total sobre el ciclo de vida del proceso (ej: en el
    * proceso principal de Electron para gestionar cierres manuales).
    */
    static async startWithHandle(opts: NovaCoreEngineOptions): Promise<{
        client: NovaCoreClient;
        process: EngineProcess;
    }> {
        const proc = new EngineProcess(opts);
        const info = await proc.start();

        const client = new NovaCoreClient({
            httpUrl: info.httpUrl,
            wsUrl: info.wsUrl,
            token: info.token,
            timeoutMs: opts.client?.timeoutMs,
            autoReconnect: opts.client?.autoReconnect,
        });

        await client.connect();
        return { client, process: proc };
    }
}