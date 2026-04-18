import { NovaCoreEngine, NovaCoreClient } from "../dist/index.js";
import path from "path";

/**
 * @typedef {import("../dist/types/index.js").InstallRequest} InstallRequest
 */

/**
 * EJEMPLO 02: Eficiencia con Shared Cache
 * Muestra la ventaja de descargar en sharedPath.
 * Instalamos la misma version dos veces y comprobamos como la
 * segunda vez se salta casi el 100% gracias a SHA-1.
 */
async function main() {
    console.log("Levantando NovaCore-Engine...");
    
    /** @type {NovaCoreEngine} */
    const engine = new NovaCoreEngine({
        instancesDir: path.resolve("./.minecraft/instances"),
    });
    
    await engine.start();
    /** @type {NovaCoreClient} */
    const client = new NovaCoreClient({ token: engine.accessToken });
    await client.connect();

    const SHARED_DIR = path.resolve("./.minecraft/shared");

    try {
        console.log("\n==================================");
        console.log("FASE 1: Descarga fria (Instancia A)");
        console.log("==================================");
        
        /** @type {InstallRequest} */
        const installReq1 = {
            version: "1.21.1",
            instancePath: path.resolve("./.minecraft/instances/InstanciaA"),
            sharedPath: SHARED_DIR,
            download: { client: true, libraries: true, assets: true, natives: true, jvm: true },
            verifySHA1: true
        };

        let startA = Date.now();
        await client.install(installReq1, {
            onProgress: (snap) => process.stdout.write(`\rProgreso: ${snap.overallPercent}% | Bytes: ${snap.downloadedBytes}/${snap.totalBytes}`),
            onCompleted: (snap) => console.log(`\nInstalacion Completada. Total bajados: ${snap.completedFiles} archivos. Duracion: ${(Date.now() - startA) / 1000}s`)
        });

        console.log("\n==================================");
        console.log("FASE 2: Reciclaje SHA-1 (Instancia B)");
        console.log("==================================");
        console.log("Instalando ESA MISMA version en otra ruta paralela. SkippedFiles sera alto.");
        
        /** @type {InstallRequest} */
        const installReq2 = {
            version: "1.21.1",
            instancePath: path.resolve("./.minecraft/instances/InstanciaB"),
            sharedPath: SHARED_DIR,
            download: { client: true, libraries: true, assets: true, natives: true, jvm: true },
            verifySHA1: true
        };

        let startB = Date.now();
        await client.install(installReq2, {
            onProgress: (snap) => process.stdout.write(`\rProgreso: ${snap.overallPercent}% | Skipped: ${snap.skippedFiles}/${snap.totalFiles}`),
            onCompleted: (snap) => {
                console.log(`\nInstalacion Clon completada. Total realmente bajados: ${snap.completedFiles}`);
                console.log(`Skipped (reusados): ${snap.skippedFiles} archivos. Duracion: ${(Date.now() - startB) / 1000}s.`);
            }
        });

    } catch (e) {
        console.error("\nError durante las descargas:", e);
    } finally {
        await client.closeEngine();
        await engine.stop();
    }
}

main().catch(console.error);
