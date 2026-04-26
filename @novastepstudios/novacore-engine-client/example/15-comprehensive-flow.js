import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 15: Flujo de Ciclo de Vida Completo
 * Desde el arranque del motor hasta la ejecución del juego en una sola pasada.
 */
async function main() {
    console.log("🌟 INICIANDO FLUJO COMPLETO DE NOVACORE 🌟");

    // 1. Iniciar Motor
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const INSTANCE_PATH = config.instancesDir + "/full-flow-test";

        // 3. Instalar
        console.log(`\n1️⃣  Instalando archivos necesarios...`);
        await client.install({
            version: "1.21.1",
            instancePath: INSTANCE_PATH,
            sharedPath: config.sharedDir,
            isInstance: true,
            download: { client: true, libraries: true, assets: true, natives: true }
        }, {
            onProgress: (p) => process.stdout.write(`\r📥 Descargando: ${p.percent}%`)
        });

        // 4. Lanzar
        console.log(`\n\n2️⃣  Lanzando el juego...`);
        const handle = await client.launch({
            version: "1.21.1",
            instancePath: INSTANCE_PATH,
            auth: { username: "NovaExplorer", userType: "offline" }
        }, {
            onStart: (id, pid) => console.log(`🚀 JUEGO CORRIENDO (PID ${pid})`),
            onLog: (l) => {
                if (l.message.includes("Stopping!")) console.log("👋 El juego está cerrando...");
            }
        });

        // 5. Esperar cierre
        await handle.exited;
        console.log("\n3️⃣  Juego terminado.");

    } catch (e) {
        console.error("\n❌ ERROR EN EL FLUJO:", e.message);
    } finally {
        // 6. Limpieza final
        await client.closeEngine();
        client.disconnect();
        console.log("\n✨ Proceso finalizado correctamente.");
    }
}

main().catch(console.error);
