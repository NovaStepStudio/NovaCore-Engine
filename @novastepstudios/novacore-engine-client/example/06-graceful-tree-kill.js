import { NovaCoreEngine, NovaCoreClient } from "../dist/index.js";
import path from "path";

/**
 * EJEMPLO 06: Graceful Shutdown (Tree-Kill Avanzado)
 * Demuestra como usar el endpoint nativo closeEngine que el equipo diseno.
 * Al llamar closeEngine, la app ordena a todas las instancias de Minecraft
 * cerrar (y mata a las rebeldes), apagando el servidor HTTP hermeticamente.
 */
async function main() {
    console.log("[Graceful-Tree-Kill] Inicializando Motor...");

    /** @type {NovaCoreEngine} */
    const engine = new NovaCoreEngine({ instancesDir: path.resolve("./.minecraft/instances") });
    await engine.start();

    /** @type {NovaCoreClient} */
    const client = new NovaCoreClient({ token: engine.accessToken });
    await client.connect();

    console.log("\nSimulando operaciones complejas...");

    console.log("\nEjecutando rutinas de auto-destruccion seguras...");

    try {
        await client.closeEngine();
        console.log("Cierre emitido correctamente. Servidor Java local apagado con normalidad.");

        await engine.stop();
        console.log("Sistema cerrado. No deberian existir procesos huérfanos.");

    } catch (e) {
        console.log("Hubo un error al intentar cierre profundo:", e);
    }
}

main().catch(console.error);
