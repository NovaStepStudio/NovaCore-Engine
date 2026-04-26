import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 08: Gestión del Java Runtime
 * Descarga automática de la versión de Java necesaria para cada Minecraft.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const instancePath = config.instancesDir + "/Vanilla-1.21.1";
        
        console.log("\n☕ Comprobando Java Runtime para 1.21.1...");
        // El motor detecta qué Java requiere la versión y lo baja si no existe
        await client.downloadRuntime("1.21.1", instancePath, config.sharedDir);
        
        console.log("✅ Runtime de Java listo para usarse.");

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
