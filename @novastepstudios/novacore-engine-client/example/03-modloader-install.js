import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 03: Instalación de ModLoaders (Forge/Fabric)
 * Demuestra cómo orquestar la instalación de cargadores de mods.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        console.log("\n🛠️  Consultando versiones de Fabric para 1.21.1...");
        const fabric = await client.getModLoaderVersions("fabric", "1.21.1");
        const latestFabric = fabric.versions[0]?.loaderVersion;
        
        console.log(`✨ Última versión de Fabric encontrada: ${latestFabric}`);

        console.log("\n📦 Instalando Fabric en la instancia...");
        await client.installModLoader({
            loader: "fabric",
            loaderVersion: latestFabric,
            minecraftVersion: "1.21.1",
            instancePath: config.instancesDir + "/Fabric-Instance",
            sharedPath: config.sharedDir
        });

        // También podemos usar el flujo unificado para instalar todo junto
        console.log("\n📥 Instalando juego completo + Forge 1.20.1...");
        await client.install({
            version: "1.20.1",
            modloader: "forge",
            // modloaderVersion: "47.2.0", // Opcional, si no se envía usa la recomendada
            instancePath: config.instancesDir + "/Forge-1.20.1",
            sharedPath: config.sharedDir,
            download: { client: true, libraries: true, assets: true, natives: true }
        }, {
            onProgress: (p) => process.stdout.write(`\r🚀 Progreso: ${p.percent}% | ${p.message}`),
            onComplete: (v, ml) => console.log(`\n✅ ${v} con ${ml} instalado correctamente.`)
        });

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
