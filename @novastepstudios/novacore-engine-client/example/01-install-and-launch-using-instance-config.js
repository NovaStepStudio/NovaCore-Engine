import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    const INSTANCE_PATH = config.instancesDir + "/aventura-1_21-3791989d";

    try {
        await client.install({
            version: "1.21.1",
            instancePath: INSTANCE_PATH,
            sharedPath: config.sharedDir,
            isInstance: true,
            download: { client: true, libraries: true, assets: true, natives: true, jvm: true },
            verifySHA1: true
        });

        const handle = await client.launch({
            version: "1.21.1",
            instancePath: INSTANCE_PATH,
            sharedPath: config.sharedDir,
            auth: {
                username: "NovaPlayer",
                uuid: "00000000-0000-0000-0000-000000000000",
                accessToken: "offline-token",
                userType: "offline"
            }
        }, {
            onStart: (id, pid) => console.log(`PID: ${pid} | LaunchID: ${id}`),
        });

        await handle.exited;
    } catch (e) {
        console.error("Error:", e.message);
    }
}

main().catch(console.error);
