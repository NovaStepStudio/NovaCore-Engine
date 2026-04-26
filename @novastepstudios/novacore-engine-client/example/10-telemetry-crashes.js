import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const crash = await client.getLatestCrash();

        if (crash) {
            console.log(crash);
        } else {
            console.log("No crash.");
        }

        const sessions = await client.getSessions();
        sessions.forEach(s => {
            const status = s.status ?? "desconocido";
            const version = s.version ?? s.instanceId ?? "sin versión";
            const files = s.completedFiles ?? s.totalFiles ?? 0;
            console.log(` - [${status}] ${version} (${files} archivos)`);
        });

    } catch (e) {
        console.error("Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
