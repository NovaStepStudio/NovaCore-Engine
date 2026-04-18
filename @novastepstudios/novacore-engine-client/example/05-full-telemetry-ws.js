import { NovaCoreEngine, NovaCoreClient } from "../dist/index.js";
import path from "path";

/**
 * EJEMPLO 05: Telemetria Total
 * Nos adherimos al historial del WebSocket. Todo lo que el motor procese
 * lograra pasar por nuestra vista sin procesar.
 */
async function main() {
    console.log("Configurando Motor y Cliente NovaCore...");
    
    /** @type {NovaCoreEngine} */
    const engine = new NovaCoreEngine({ instancesDir: path.resolve("./.minecraft/instances") });
    await engine.start();

    /** @type {NovaCoreClient} */
    const client = new NovaCoreClient({ token: engine.accessToken });

    // Interceptamos antes de la conexion
    client.onAny((event, data) => {
        if (event !== "download_progress" && event !== "game_log" && event !== "session_progress") {
            const extraInfo = JSON.stringify(data);
            console.log(`[WS Evento Puro] -> ${String(event).padEnd(20)} | ${extraInfo.substring(0, 100)}...`);
        }
    });

    client.on("recovery_state", (state) => {
        console.log(`\nNueva alerta: El motor detecto ${state.sessions.length} descargas caidas previas.`);
        console.log("Listas para retomar...");
    });

    await client.connect();
    
    console.log("\nSolicitamos operacion interna para revisar la telemetria real...");
    try {
        await client.downloadRuntime("1.21.1", path.resolve("./.minecraft/instances/telemetryTest"));
        await new Promise(r => setTimeout(r, 8000));
    } catch(e) {}
    
    console.log("Fin del registro. Apagando.");
    await client.closeEngine();
    await engine.stop();
}

main().catch(console.error);
