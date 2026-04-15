import { NovaCoreEngine } from '@novastepstudios/novacore-engine-client';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

console.log('--- Iniciando NovaCore Engine ---');

const client = await NovaCoreEngine.start({
    jar: path.join(__dirname, '../../../core/build/libs/novacore-engine.jar'),
    instancesDir: path.join(__dirname, '.minecraft', 'instances'),
    logDir: path.join(__dirname, 'logs'),
    launcherName: 'NovaLauncher',
    threads: 8,
    verbose: false,
});

console.log('Comenzando instalacion de Minecraft...\n');

try {
    await client.install(
        {
            version: '1.21.4',
            instancePath: path.join(__dirname, '.minecraft', 'instances', 'vanilla-1.21.4'),
            sharedPath: path.join(__dirname, '.minecraft', 'shared'), 
            download: {
                client: true,
                libraries: true,
                assets: true,
                natives: true,
            },
            verifySHA1: true,
            maxThreads: 32,
            launcher: {
                name: 'NovaLauncher',
                version: '1.0.0'
            }
        },
        {
            onStart: (totalFiles, totalBytes) => {
                const sizeMb = (totalBytes / 1_048_576).toFixed(2);
                console.log(`Tareas: ${totalFiles} archivos | Tamano: ${sizeMb} MB\n`);
            },
            onProgress: ({ percent, downloadedMb, totalMb, message }) => {
                const barLength = 40;
                const filled = Math.round((percent / 100) * barLength);
                const bar = '='.repeat(filled) + '-'.repeat(barLength - filled);
                
                const stats = `${downloadedMb.toFixed(1)}/${totalMb.toFixed(1)} MB`.padStart(15);
                const pct = `${percent}%`.padStart(5);
                
                process.stdout.write(`\r[${bar}] ${pct} | ${stats} | ${message.slice(0, 30).padEnd(30)}`);
            },
            onModule: ({ module, status }) => {
                if (status === 'completed') {
                    console.log(`\nOK: Modulo [${module.toUpperCase()}] completado.`);
                }
            },
            onComplete: (version) => {
                console.log('\n\nINSTALACION FINALIZADA CON EXITO');
                console.log(`Version lista: ${version}\n`);
            },
            onError: (reason) => {
                console.error('\n\nERROR DURANTE LA INSTALACION:');
                console.error(`Razon: ${reason}\n`);
            },
        },
        1200_000 
    );

    console.log('Cerrando motor...');
    process.exit(0);
} catch (error) {
    console.error('\nFallo critico:', error.message);
    process.exit(1);
}