import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const installReq = {
            version: "1.21.1",
            instancePath: config.instancesDir + "/Stepnicka012",
            sharedPath: config.sharedDir,
            isInstance: true,
            download: { client: true, libraries: true, assets: true, natives: true, jvm: true },
            verifySHA1: true
        };

        await client.install(installReq, {
            onStart: (totalFiles, totalBytes) => {
                console.log(`Preparado: ${totalFiles} archivos (~${(totalBytes / 1024 / 1024).toFixed(2)} MB)`);
            },
            onProgress: (p) => {
                const bar = "█".repeat(Math.floor(p.percent / 5)) + "░".repeat(20 - Math.floor(p.percent / 5));
                process.stdout.write(`\r${bar} ${p.percent}% | ${p.completedFiles}/${p.totalFiles} archivos | ${p.downloadedMb}/${p.totalMb} MB`);
            },
            onModule: (m) => {
                console.log(`\nMódulo: ${m.module} (${m.status})`);
            },
            onComplete: (version) => {
                console.log(`\nOK: ${version}`);
            }
        });

    } catch (e) {
        console.error("Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
