import { NovaCoreEngine } from '../dist/index';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

console.log('--- Iniciando NovaCore Engine ---\n');

const client = await NovaCoreEngine.start({
    jar: path.join(__dirname, '../../../core/build/libs/novacore-engine.jar'),
    instancesDir: path.join(__dirname, '.minecraft', 'instances'),
    verbose: false,
});

console.log('--- Lanzando Minecraft (Fabric) ---\n');

try {
    const handle = await client.launch(
        {
            version: '1.21.1',
            instancePath: path.join(__dirname, '.minecraft', 'instances', 'fabric-1.21.1'),
            modloader: 'fabric',
            modloaderVersion: '0.16.10',
            auth: {
                username: 'FabricPlayer',
                uuid: '0',
                accessToken: '0',
                userType: 'offline',
            },
            launcher: {
                name: 'NovaLauncher',
            },
        },
        {
            onStart: (launchId, pid) => {
                console.log(`OK: Instancia iniciada (PID: ${pid})`);
            },
            onLog: ({ level, message }) => {
                console.log(`[${level}] ${message}`);
            },
            onCrashExit: (launchId, exitCode, reason) => {
                console.error(`\nCRASH DETECTADO: ${reason} (Codigo: ${exitCode})`);
            },
            onExit: (launchId, durationMs) => {
                console.log(`\nInstancia de Fabric cerrada.`);
                console.log(`Duracion de la sesion: ${(durationMs / 1000).toFixed(1)}s`);
            },
            onLaunchFailed: (error) => {
                console.error('\nFallo al lanzar Fabric:', error);
            },
        }
    );

    console.log('Esperando cierre del juego...');
    await handle.exited;
    
    console.log('\nProceso finalizado.');
    process.exit(0);
} catch (error) {
    console.error('Error fatal:', error);
    process.exit(1);
}