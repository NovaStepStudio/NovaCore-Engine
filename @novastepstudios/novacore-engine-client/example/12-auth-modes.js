import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 12: Modos de Autenticación
 * Comparativa entre el lanzamiento Offline y Microsoft Authentication.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    // 1. Modo Offline (No verificado por Mojang)
    const offlineAuth = {
        username: "NovaStepPlayer",
        userType: "offline"
    };

    // 2. Modo Microsoft (Requiere accessToken real de Xbox/Mojang)
    const onlineAuth = {
        username: "VerifiedUser",
        uuid: "12345678-1234-1234-1234-123456789012",
        accessToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        userType: "msa" // o "mojang"
    };

    console.log("\n🔑 Demostrando configuración de autenticación...");
    console.log(`Modo Offline: ${offlineAuth.username}`);
    console.log(`Modo Online:  ${onlineAuth.username} (${onlineAuth.userType})`);

    // El objeto auth se pasa directamente al método launch()
    // client.launch({ ..., auth: offlineAuth });

    await client.closeEngine();
    client.disconnect();
}

main().catch(console.error);
