import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const config = {
    jarPath: path.resolve(__dirname, "../../../core/build/libs/novacore-engine-2.1.0.jar"),
    instancesDir: path.resolve(__dirname, ".minecraft"),
    sharedDir: path.resolve(__dirname, ".minecraft/shared"),
    logDir: path.resolve(__dirname, ".minecraft/logs"),
    engineOptions: {
        verbose: false,
        logLevel: "INFO",
        launcherName: "NovaCore_Example",
    }
};

export function toMB(bytes) {
    return Math.round((bytes / 1024 / 1024) * 100) / 100;
}
