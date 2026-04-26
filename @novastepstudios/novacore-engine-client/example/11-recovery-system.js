import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 11: Sistema de Recuperación
 * Cómo detectar instalaciones interrumpidas y reanudarlas sin perder el progreso.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        console.log("\n🔄 Buscando sesiones interrumpidas que se puedan recuperar...");
        const recovery = await client.getRecoverySessions();

        if (recovery.length === 0) {
            console.log("No hay sesiones pendientes de recuperación.");
        } else {
            console.log(`Se encontraron ${recovery.length} sesiones:`);
            for (const session of recovery) {
                console.log(` - ${session.version} (Progreso: ${session.overallPercent}%)`);
                
                console.log(`⏯️  Reanudando sesión ${session.sessionId}...`);
                await client.resumeInstall(session.sessionId);
                
                // Podemos esperar a que termine usando el mismo flujo de siempre
                await client.install({ 
                    version: session.version,
                    instancePath: session.instancePath,
                    sessionId: session.sessionId // El motor sabe que es una recuperación
                }, {
                    onProgress: (p) => process.stdout.write(`\rReanudando: ${p.percent}%`)
                });
                
                console.log("\n✅ Sesión recuperada y completada.");
                break; // Solo recuperamos la primera para este ejemplo
            }
        }

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
