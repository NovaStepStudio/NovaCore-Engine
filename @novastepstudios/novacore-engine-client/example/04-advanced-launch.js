import { NovaCoreEngine, NovaCoreClient } from "../dist/index.js";
import path from "path";

/**
 * @typedef {import("../dist/types/index.js").LaunchRequest} LaunchRequest
 */

/**
 * EJEMPLO 04: Advanced Launch Options
 * Pide al motor que evalue nuestra RAM/CPU dinamicamente y usamos esa metrica
 * junto a modificadores GPU / GC Avanzado para configurar el juego.
 */
async function main() {
    /** @type {NovaCoreEngine} */
    const engine = new NovaCoreEngine({ instancesDir: path.resolve("./.minecraft/instances") });
    await engine.start();

    /** @type {NovaCoreClient} */
    const client = new NovaCoreClient({ token: engine.accessToken });
    await client.connect();

    try {
        console.log("1. Auditando sistema con NovaCore...");
        const pcInfo = await client.getEngineInfo();
        console.log(`Tenemos ${pcInfo.cpu.cores} nucleos y ${pcInfo.ram.totalMb} MB totales de RAM.`);
        console.log(`Recomendacion: MAX ${pcInfo.recommended.mcMaxRamMb} MB.`);

        const myInstance = path.resolve("./.minecraft/instances/PerfWorld");

        console.log("\n2. Disparando el juego con los mejores settings computados...");
        
        /** @type {LaunchRequest} */
        const launchReq = {
            version: "1.21.1",
            instancePath: myInstance,
            hardwareAcceleration: true,
            gpuPreference: "dgpu",
            gcPreset: pcInfo.recommended.gcPreset,
            jvm: {
                minMemoryMb: pcInfo.recommended.mcMinRamMb,
                maxMemoryMb: pcInfo.recommended.mcMaxRamMb,
                extraArgs: ["-Duser.language=es", "-Ddisable.telemetry=true"]
            },
            auth: {
                username: "CyberPlayer2099",
                uuid: "00000000-0000-0000-0000-000000000000",
                accessToken: "0"
            },
            game: {
                serverHost: "play.hypixel.net",
                serverPort: 25565
            }
        };

        const { launchId } = await client.launch(launchReq);
        console.log(`Juego arrancando. ID Perfil: ${launchId}`);

        setTimeout(async () => {
            console.log("\nMatando proceso despues de 5 segundos...");
            await client.killInstance(launchId);
        }, 5000);

        const result = await client.waitFor("game_crash", 10000).catch(() => null);
        if (result) console.log(`Crash o Kill detectado con codigo: ${result.exitCode}`);

    } catch (e) {
        console.error("Algo fallo durante el lanzamiento:", e);
    } finally {
        await client.closeEngine();
        await engine.stop();
    }
}
main().catch(console.error);
