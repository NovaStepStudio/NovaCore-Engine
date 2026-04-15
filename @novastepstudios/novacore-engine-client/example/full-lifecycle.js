import { NovaCoreEngine } from '@novastepstudios/novacore-engine-client';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Este ejemplo demuestra el ciclo de vida completo recomendado:
 * 1. Iniciar el Engine (proceso Java).
 * 2. Instalar una versión (si no existe).
 * 3. Lanzar el juego.
 * 4. Cerrar el Engine limpiamente al terminar.
 */

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Configuración de rutas
const JAR_PATH = path.join(__dirname, '../../../core/build/libs/novacore-engine.jar');
const BASE_DIR = path.join(__dirname, '.minecraft');
const INSTANCE_PATH = path.join(BASE_DIR, 'instances', 'vanilla-1.21.4');

async function run() {
    console.log('--- [1/4] Iniciando NovaCore Engine ---');
    
    // El EngineProcess se encarga de lanzar el JAR y darnos un cliente conectado
    const { client, process: engineProcess } = await NovaCoreEngine.startWithHandle({
        jar: JAR_PATH,
        instancesDir: path.join(BASE_DIR, 'instances'),
        launcherName: 'ExampleLauncher',
        verbose: false
    });

    try {
        console.log('\n--- [2/4] Verificando/Instalando Minecraft 1.21.4 ---');
        
        await client.install({
            version: '1.21.4',
            instancePath: INSTANCE_PATH,
            sharedPath: path.join(BASE_DIR, 'shared'),
            launcher: { name: 'ExampleLauncher' }
        }, {
            onProgress: ({ percent, message }) => {
                process.stdout.write(`\r   Progreso: ${percent}% | ${message.padEnd(40)}`);
            }
        });

        console.log('\n\n--- [3/4] Lanzando el juego ---');
        
        const handle = await client.launch({
            version: '1.21.4',
            instancePath: INSTANCE_PATH,
            auth: { username: 'Player', uuid: '0', accessToken: '0', userType: 'offline' },
            launcher: { name: 'ExampleLauncher' }
        }, {
            onStart: (id, pid) => console.log(`   Juego en ejecucion (PID: ${pid})`),
            onLog: ({ level, message }) => {
                // Solo mostramos logs importantes para no saturar la consola
                if (level === 'ERROR' || level === 'FATAL') {
                    console.log(`   [${level}] ${message}`);
                }
            }
        });

        console.log('\n--- Esperando a que el juego termine ---');
        await handle.exited;
        
        console.log('\n--- [4/4] Juego cerrado. Finalizando motor ---');
        
    } catch (err) {
        console.error('\nError en el flujo:', err.message);
    } finally {
        // MUY IMPORTANTE: Asegurarse de cerrar el proceso Java
        await engineProcess.stop();
        console.log('Ciclo finalizado.');
        process.exit(0);
    }
}

run().catch(console.error);
