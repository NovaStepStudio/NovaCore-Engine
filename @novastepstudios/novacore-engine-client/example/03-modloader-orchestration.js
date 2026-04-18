import { NovaCoreEngine, NovaCoreClient } from "../dist/index.js";
import path from "path";

/**
 * @typedef {import("../dist/types/index.js").ModLoaderRequest} ModLoaderRequest
 */

/**
 * EJEMPLO 03: Orquestacion de ModLoaders
 * Consulta motores listados (Fabric/Forge), pide versiones e 
 * inicia instalacion asincrona de mods.
 */
async function main() {
    console.log("Levantando NovaCore-Engine...");
    
    /** @type {NovaCoreEngine} */
    const engine = new NovaCoreEngine({ instancesDir: path.resolve("./.minecraft/instances") });
    await engine.start();

    /** @type {NovaCoreClient} */
    const client = new NovaCoreClient({ token: engine.accessToken });
    await client.connect();

    try {
        console.log("\n[1] Pidiendo lista de ModLoaders integrados...");
        const { loaders } = await client.getModLoaders();
        console.log(`Soportados: ${loaders.join(", ")}`);

        if (!loaders.includes("fabric")) return;

        console.log("\n[2] Consultando versiones en linea de Fabric para 1.21.1...");
        let versionsData;
        try {
            versionsData = await client.getModLoaderVersions("fabric", "1.21.1");
            console.log(`Se encontraron sub-versiones de fabric.`);
        } catch (e) {
            console.log("Verificar la respuesta remota.");
        }
        
        const targetVersion = versionsData && versionsData.versions.length > 0 ? versionsData.versions[0].id : "0.15.7";
        console.log(`Instalando Fabric -> ${targetVersion}`);

        console.log("\n[3] Ordenando instalacion tecnica asincrona...");
        const instanceTarget = path.resolve("./.minecraft/instances/FabricWorld");
        
        /** @type {ModLoaderRequest} */
        const modRequest = {
            loader: "fabric",
            minecraftVersion: "1.21.1",
            loaderVersion: targetVersion,
            resolvedInstancePath: instanceTarget,
            resolvedLibrariesPath: path.resolve("./.minecraft/shared/libraries"),
            resolvedMinecraftJar: path.resolve("./.minecraft/shared/versions/1.21.1/1.21.1.jar")
        };
        await client.installModLoader(modRequest);

        let unsub = client.on("modloader_processor_log", (log) => {
            console.log(`[Fabric-Installer] -> ${log.line}`);
        });

        await new Promise(r => setTimeout(r, 6000));
        unsub();

        console.log("\n[4] Consultando si ya quedo instalado en la instancia...");
        try {
            const state = await client.getModLoaderState(instanceTarget);
            console.log("El motor confirma instalacion en el siguiente estado:", state.modloader);
        } catch (e) {
            console.log("Instalacion pendiente.");
        }

    } catch (e) {
        console.error("Error en modulo de ModLoaders:", e.message);
    } finally {
        await client.closeEngine();
        await engine.stop();
    }
}

main().catch(console.error);
