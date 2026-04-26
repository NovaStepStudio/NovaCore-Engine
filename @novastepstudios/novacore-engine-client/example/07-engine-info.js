import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 07: Información del Sistema y Recursos
 * Cómo obtener detalles del hardware y recomendaciones del motor.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        console.log("\n🖥️  Obteniendo información del sistema...");
        const info = await client.getEngineInfo();
        const totalRamGb = (info.ram.totalMb / 1024).toFixed(2);

        console.log("-----------------------------------------");
        console.log(`Versión del Motor: ${info.version || "desconocida"}`);
        console.log(`CPU:               ${info.cpu.cores} (núcleos)`);
        console.log(`RAM Total:         ${totalRamGb} GB`);
        console.log("-----------------------------------------");

        console.log("\n💡 Optimizaciones recomendadas:");
        console.log(` - RAM para MC: ${info.recommended.mcMaxRamMb} MB`);
        console.log(` - GC Preset:   ${info.recommended.gcPreset}`);

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
