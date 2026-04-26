import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Common configuration for all examples.
 */
export const config = {
    // Path to the NovaCore-Engine JAR file
    jarPath: path.resolve(__dirname, "../../../core/build/libs/novacore-engine.jar"),
    
    // Default directory for Minecraft instances
    instancesDir: path.resolve(__dirname, ".minecraft/instances"),
    
    // Default directory for shared assets/libraries
    sharedDir: path.resolve(__dirname, ".minecraft/shared"),
    
    // Default directory for logs
    logDir: path.resolve(__dirname, ".minecraft/logs"),
    
    // Default engine options
    engineOptions: {
        verbose: false,
        logLevel: "INFO",
        launcherName: "NovaCore_Example",
    }
};

/**
 * Utility to format bytes to MB
 */
export function toMB(bytes) {
    return Math.round((bytes / 1024 / 1024) * 100) / 100;
}
