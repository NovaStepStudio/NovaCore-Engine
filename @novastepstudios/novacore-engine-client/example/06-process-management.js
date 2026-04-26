import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 06: Gestión de Procesos y Cierre Forzoso
 * Cómo listar instancias en ejecución y matarlas si el usuario lo solicita.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        console.log("\n🔍 Consultando instancias activas...");
        const instances = await client.getRunningInstances();

        if (instances.length === 0) {
            console.log("No hay instancias de Minecraft corriendo actualmente.");
        } else {
            console.log(`Hay ${instances.length} instancias en ejecución:`);
            for (const inst of instances) {
                console.log(` - ID: ${inst.launchId} | Versión: ${inst.version} | PID: ${inst.pid}`);
                
                // Si quisiéramos matar todas las de una versión específica:
                if (inst.version === "1.21.1") {
                    console.log(`🛑 Matando instancia ${inst.launchId}...`);
                    await client.killInstance(inst.launchId);
                }
            }
        }

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
