import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 14: Verificador de ModLoaders
 * Consulta el estado técnico de un cargador de mods en una carpeta.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const instances = await client.getInstancesList();
        const fabricInstance = instances.instances.find((instance) => instance.modLoader === "fabric");

        if (!fabricInstance) {
            console.log("No hay ninguna instancia con Fabric registrada. Ejecuta primero el ejemplo 03.");
            return;
        }

        const path = fabricInstance.path;

        console.log(`\n🔍 Verificando estado de ModLoader en: ${path}`);
        const state = await client.getModLoaderState(path);

        console.log(`Loader detectado: ${state.loader || "Ninguno"}`);
        console.log(`Versión:         ${state.version || "N/A"}`);
        console.log(`¿Está íntegro?:  ${state.isHealthy ? "✅ Sí" : "❌ No"}`);

        if (state.loader && !state.isHealthy) {
            console.log("⚠️  El ModLoader parece estar corrupto. Limpiando estado...");
            await client.deleteModLoaderState(path);
            console.log("Reinstala el ModLoader para solucionar el problema.");
        }

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
