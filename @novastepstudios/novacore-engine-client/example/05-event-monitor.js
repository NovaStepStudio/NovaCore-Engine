import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 05: Monitor de Eventos en Tiempo Real
 * Suscripción a eventos globales del motor para crear sistemas de telemetría o logs.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    console.log("📡 Escuchando todos los eventos del WebSocket...");

    // Escuchar cualquier evento
    client.onAny((event, data) => {
        console.log(`[WS EVENT] ${event}:`, JSON.stringify(data).slice(0, 80) + "...");
    });

    // Escuchar eventos específicos con tipado (si usas TS)
    client.on("launch_started", (d) => {
        console.log(`🚀 NUEVA INSTANCIA: ${d.launchId} (PID ${d.pid})`);
    });

    client.on("game_crash", (d) => {
        console.error(`💥 CRASH DETECTADO: ${d.reason} (Código ${d.exitCode})`);
    });

    console.log("\nEjecuta otra instancia o una instalación para ver los eventos fluir.");
    console.log("Presiona Ctrl+C para salir.");

    try {
        await new Promise((resolve) => process.once("SIGINT", resolve));
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
