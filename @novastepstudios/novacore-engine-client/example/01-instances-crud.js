import { NovaCoreEngine, LaunchRequest, NovaCoreClient } from "../dist/index";
import path from "path";

/**
 * @typedef {import("../dist/types/index.js").InstanceInfo} InstanceInfo
 */

/**
 * EJEMPLO 01: CRUD de Instancias
 * Demuestra como crear, actualizar, listar y eliminar perfiles de juego
 * persistentes a traves de NovaCore-Engine sin necesidad de bases de datos.
 */
async function main() {
    console.log("Levantando NovaCore-Engine...");

    /** @type {NovaCoreEngine} */
    const engine = new NovaCoreEngine({
        instancesDir: path.resolve("./.minecraft/instances"),
        verbose: false,
    });

    await engine.start();
    console.log("Motor en linea!");

    /** @type {NovaCoreClient} */
    const client = new NovaCoreClient({ token: engine.accessToken });
    await client.connect();

    try {
        console.log("\n[1/4] Creando Instancia 'Aventura-1.21'...");
        const instance = await client.createInstance({
            name: "Aventura-1.21",
            mcVersion: "1.21.1",
            config: {
                modLoader: "vanilla",
                minMemoryMb: 1024,
                maxMemoryMb: 4096,
                gcPreset: "zgc",
            }
        });
        console.log(`Instancia creada. ID UUID: ${instance.id} | Ruta: ${instance.path}`);

        console.log("\n[2/4] Listando todas las instancias disponibles...");
        const list = await client.getInstancesList();
        console.log(`Hay ${list.count} instancias registradas.`);
        list.instances.forEach((inst) => {
            console.log(`- ${inst.name} [v${inst.mcVersion}] (Configurada a ${inst.config.maxMemoryMb || 1024}MB)`);
        });

        console.log("\n[3/4] Actualizando configuracion de la instancia...");
        await client.updateInstance(instance.id, {
            maxMemoryMb: 2048,
            gcPreset: "g1gc_optimized"
        });
        console.log("Instancia actualizada con exito.");

        console.log("\n[4/4] Eliminando registro de la instancia...");
        await client.deleteInstance(instance.id);
        console.log("Registro de instancia purgado.");

    } catch (e) {
        console.error("Error en el API de instancias:", e.message);
    } finally {
        await client.closeEngine();
        await engine.stop();
        console.log("\nEngine apagado con exito.");
    }
}

main().catch(console.error);
