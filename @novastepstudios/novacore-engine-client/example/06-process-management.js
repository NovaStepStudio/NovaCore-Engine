import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

async function main() {
    const { client, process: proc } = await NovaCoreEngine.startWithHandle({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    console.log("Engine running. PID:", proc.info?.pid);
    console.log("Token:", proc.info?.token);

    client.on("engine_unreachable", (d) => {
        console.error("Engine crashed or disconnected:", d.reason);
    });

    const alive = await proc.healthCheck();
    console.log("Health check:", alive ? "OK" : "FAIL");

    console.log("Engine will auto-cleanup on process exit.");
}

main().catch(console.error);
