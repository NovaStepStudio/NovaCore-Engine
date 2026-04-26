import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 04: Lanzamiento Básico del Juego
 * Cómo iniciar Minecraft con una configuración mínima y capturar los logs iniciales.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        console.log("\n🎮 Lanzando Minecraft 1.21.1...");

        const handle = await client.launch({
            version: "1.21.1",
            instancePath: config.instancesDir + "/Stepnicka012",
            sharedPath: config.sharedDir,
            auth: {
                username: "NovaPlayer",
                uuid: "00000000-0000-0000-0000-000000000000",
                accessToken: "offline-token",
                userType: "offline"
            },
        }, {
            onStart: (id, pid) => {
                console.log(`🚀 Juego iniciado! PID: ${pid} | LaunchID: ${id}`);
            },
            onLog: (log) => {
                console.log(`[GAME] [${log.level}] ${log.message}`);
            },
            onExit: (id, duration) => {
                console.log(`\n👋 Juego cerrado tras ${Math.round(duration / 1000)}s.`);
            }
        });

        // console.log("⏳ Esperando 10 segundos antes de cerrar el juego automáticamente...");
        // await new Promise(r => setTimeout(r, 10000));

        // console.log("🛑 Cerrando el juego forzosamente...");
        // await handle.kill();
        await handle.exited;
    } catch (e) {
        console.error("\n❌ Error al lanzar:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
