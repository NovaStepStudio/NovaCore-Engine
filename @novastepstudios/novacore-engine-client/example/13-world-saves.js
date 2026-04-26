import { NovaCoreEngine } from "../dist/index.js";
import { config } from "./00-config.js";

/**
 * EJEMPLO 13: Explorador de Mundos
 * Cómo listar las partidas guardadas en una instancia específica.
 */
async function main() {
    const client = await NovaCoreEngine.start({
        jar: config.jarPath,
        instancesDir: config.instancesDir,
        ...config.engineOptions
    });

    try {
        const data = await client.getWorlds();
        console.log("\n" + JSON.stringify(data, null, 2));
        const worlds = data.worlds;

        if (worlds.length === 0) {
            console.log("No se encontraron mundos guardados.");
        } else {
            console.log(`Se encontraron ${worlds.length} mundos:`);
            worlds.forEach(w => {
                const date = new Date(w.lastPlayed).toLocaleString();
                console.log(` - ${w.levelName} [Folder: ${w.folderName}] (${w.versionName})`);
                console.log(`   Última vez: ${date}`);
                console.log(`   Ruta: ${w.path}`);
                if (w.iconBase64) console.log(`   Icono: [Presente]`);
            });
        }

    } catch (e) {
        console.error("\n❌ Error:", e.message);
    } finally {
        await client.closeEngine();
        client.disconnect();
    }
}

main().catch(console.error);
