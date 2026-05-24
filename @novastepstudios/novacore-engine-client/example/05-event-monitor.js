import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    client.on("connected", (d) => console.log("Connected:", d.message));
    client.on("install_step", (d) => console.log(`[${d.sessionId}] Step: ${d.step}`));
    client.on("session_progress", (d) => {
        process.stdout.write(`\rProgress: ${d.overallPercent}% (${d.completedFiles}/${d.totalFiles})`);
    });
    client.on("launch_started", (d) => console.log(`\nGame started (PID: ${d.pid})`));
    client.on("launch_exited", (d) => console.log(`Game exited (code: ${d.exitCode}, duration: ${d.durationMs}ms)`));
    client.on("game_log", (l) => {
        if (l.level === "ERROR" || l.level === "FATAL") {
            console.log(`[${l.level}] ${l.message}`);
        }
    });
    client.on("engine_unreachable", (d) => {
        console.error(`\nEngine unreachable! Reason: ${d.reason}`);
    });

    console.log("Listening for events. Run another example to see output.");
    await new Promise(() => {});
}

main().catch(console.error);
