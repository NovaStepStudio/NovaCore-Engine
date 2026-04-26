import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 09: Personalización de la JVM
 * Cómo gestionar argumentos de Java y preajustes (presets) de Garbage Collector.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });
    let instanceId = null;

    try {
        const instance = await client.createInstance({
            name: "JVM-Tuning-Demo",
            mcVersion: "1.21.1",
            config: {
                modLoader: "vanilla",
                maxMemoryMb: 2048
            }
        });
        instanceId = instance.id;
        
        console.log("\n⚙️  Configurando argumentos JVM para la instancia...");
        
        // 1. Obtener argumentos actuales
        const currentArgs = await client.getInstanceJvmArgs(instanceId);
        console.log("Argumentos actuales:", currentArgs);

        // 2. Sobrescribir con nuevos argumentos
        await client.updateInstanceJvmArgs(instanceId, [
            "-Xmx4G",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseG1GC"
        ]);
        
        // 3. O simplemente usar un preset en la config de la instancia
        await client.updateInstance(instanceId, {
            gcPreset: "zgc" // "g1gc_optimized", "zgc", "shenandoah", "default"
        });

        console.log("✅ Configuración de la JVM actualizada.");

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        if (instanceId) {
            await client.deleteInstance(instanceId).catch(() => {});
        }
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
