import { NovaCoreEngine } from '@novastepstudios/novacore-engine-client';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

console.log('--- Iniciando NovaCore Engine ---\n');

const client = await NovaCoreEngine.start({
    jar: path.join(__dirname, '../../../core/build/libs/novacore-engine.jar'),
    instancesDir: path.join(__dirname, '.minecraft', 'instances'),
    verbose: false,
    launcherName: 'NovaLauncher',
});

console.log('--- Lanzando Minecraft ---\n');

try {
    const handle = await client.launch(
        {
            version: '1.21.4',
            instancePath: path.join(__dirname, '.minecraft', 'instances', 'vanilla-1.21.4'),
            sharedPath: path.join(__dirname, '.minecraft', 'shared'),
            auth: {
                username: 'NovastepPlayer',
                uuid: '00000000-0000-0000-0000-000000000000',
                accessToken: '00000000-0000-0000-0000-000000000000',
                userType: 'offline',
            },
            jvm: {
                maxMemoryMb: 4096,
            },
            gcPreset: 'g1gc_optimized',
            launcher: {
                name: 'NovaLauncher',
                version: '1.0.0',
            },
        },
        {
            onStart: (launchId, pid) => {
                console.log(`OK: Juego iniciado con exito (PID: ${pid})`);
                console.log(`ID de lanzamiento: ${launchId}\n`);
            },
            onLog: ({ level, logger, message }) => {
                const gray = (s) => `\x1b[90m${s}\x1b[0m`;
                const blue = (s) => `\x1b[34m${s}\x1b[0m`;
                const yellow = (s) => `\x1b[33m${s}\x1b[0m`;
                const red = (s) => `\x1b[31m${s}\x1b[0m`;

                let tag = `[${level}]`;
                if (level === 'INFO') tag = blue(tag);
                else if (level === 'WARN') tag = yellow(tag);
                else if (level === 'ERROR' || level === 'FATAL') tag = red(tag);

                console.log(`${tag} ${gray(logger + ':')} ${message}`);
            },
            onCrashExit: (launchId, exitCode, reason) => {
                console.error(`\nCRASH DETECTADO (Codigo ${exitCode})`);
                console.error(`Razon: ${reason}\n`);
            },
            onExit: (launchId, durationMs) => {
                console.log(`\nInstancia cerrada.`);
                console.log(`Tiempo de juego: ${(durationMs / 1000).toFixed(1)} segundos`);
            },
            onLaunchFailed: (error) => {
                console.error('\nError al intentar lanzar:', error);
            },
        }
    );

    console.log('Esperando a que el juego se cierre...');
    await handle.exited;
    
    console.log('\nProceso finalizado.');
    process.exit(0);
} catch (error) {
    console.error('Error fatal:', error);
    process.exit(1);
}