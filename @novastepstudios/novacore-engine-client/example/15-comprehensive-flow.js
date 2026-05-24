import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

async function main() {
    console.log("Starting full NovaCore flow...");

    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const INSTANCE_PATH = config.instancesDir + "/full-flow-test";

        console.log("Installing files...");
        await client.install({
            version: "1.21.1",
            instancePath: INSTANCE_PATH,
            sharedPath: config.sharedDir,
            isInstance: true,
            download: { client: true, libraries: true, assets: true, natives: true }
        }, {
            onProgress: (p) => process.stdout.write(`\rDownloading: ${p.percent}%`)
        });

        console.log("\nLaunching game...");
        const handle = await client.launch({
            version: "1.21.1",
            instancePath: INSTANCE_PATH,
            auth: { username: "NovaExplorer", userType: "offline" }
        }, {
            onStart: (id, pid) => console.log(`Game running (PID ${pid})`),
        });

        await handle.exited;
        console.log("Game finished.");

    } catch (e) {
        console.error("Error:", e.message);
    }
}

main().catch(console.error);
