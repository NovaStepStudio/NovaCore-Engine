'use strict';

const path        = require('path');
const CoreProcess = require('../CoreProcess');
const CoreClient  = require('../CoreClient');
const {
    JAR_PATH, MC_VERSION, SHARED_DIR, INSTANCES_DIR,
    LOG_DIR, LAUNCHER_NAME, DEFAULT_AUTH, DEFAULT_WINDOW,
} = require('./config');

const INSTANCE_PATH = path.join(INSTANCES_DIR, 'vanilla-test-1');

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║   novacore-engine — Advanced Launch      ║');
    console.log('╚══════════════════════════════════════════╝\n');
    
    const proc = new CoreProcess({
        jarPath:      JAR_PATH,
        instancesDir: INSTANCES_DIR,
        logDir:       LOG_DIR,
        launcherName: LAUNCHER_NAME,
        verbose:      false,
    });
    process.on('SIGINT', async () => { await proc.stop(); process.exit(0); });
    await proc.start();
    proc.on('stderr', (line) => { if (!line.includes('WARNING')) console.error('[Java]', line); });
    
    const client = new CoreClient();
    await client.connect();
    
    client.on('debug', (d) => console.log(`  [core] ${d.message}`));
    
    client.on('launch_command_ready', (d) => {
        console.log('\n[Launch] Comando completo:');
        d.command.forEach((arg, i) => {
            let prefix = '       ';
            if (arg.startsWith('-X') || arg.startsWith('-D') || arg.startsWith('-XX')) prefix = '  JVM  ';
            else if (arg.startsWith('--')) prefix = '  GAME ';
            console.log(`${prefix} [${String(i).padStart(2)}] ${arg}`);
        });
        console.log();
    });
    
    client.on('launch_started', (d) => {
        console.log('╔══════════════════════════════════════════╗');
        console.log('║           Minecraft iniciado!            ║');
        console.log('╚══════════════════════════════════════════╝');
        console.log(`  Java:    ${d.javaExec}`);
        console.log(`  Offline: ${d.offline}`);
        console.log(`  Authlib: ${d.authlib}`);
        console.log('\n─── Game Logs ──────────────────────────────');
    });
    
    client.on('game_log', (d) => console.log(' >', d.line));
    client.on('launch_failed', (d) => console.error('\n✗', d.error));
    
    console.log('Lanzando con todas las opciones...\n');
    
    const { launchId } = await client.launch({
        version:      MC_VERSION,
        instancePath: INSTANCE_PATH,
        sharedPath:   SHARED_DIR,
        
        auth: DEFAULT_AUTH,
        
        authlibInjector: {
            enabled:   false,
            jarPath:   path.join(__dirname, '../../../authlib-injector.jar'),
            serverUrl: 'https://skin.example.com/api/yggdrasil',
        },
        
        jvm: {
            minMemoryMb:  512,
            maxMemoryMb:  4096,
            prependArgs:  [],
            extraArgs:    [
                // '-verbose:gc',
                // '-Djava.awt.headless=false',
            ],
        },
        
        window: DEFAULT_WINDOW,
        
        gcPreset:     'auto',
        
        hardwareAcceleration: false,
        
        gpuPreference: 'auto',
        
        launcher: {
            name:    LAUNCHER_NAME,
            version: '1.0.0',
        },
        
        features: {
            // demo: true,
            // quickPlay: { mode: 'multiplayer', value: 'mc.hypixel.net:25565' },
            // quickPlay: { mode: 'singleplayer', value: 'Mi Mundo' },
        },
        
        game: {
            // gameDir:  '/ruta/custom/para/saves',
            extraGameArgs:    [],
            extraJvmProperties: {
                // 'my.custom.prop': 'value',
            },
            disableMultiplayer: false,
            disableChat:        false,
            // serverHost: 'mc.hypixel.net',
            // serverPort: 25565,
        },
    });
    
    console.log(`\nLaunch ID: ${launchId}`);
    
    const result = await client.waitForGame(launchId);
    console.log('\n─────────────────────────────────────────────');
    console.log(result.status === 'clean' ? `✓ Exit: ${result.exitCode}` : `✗ Crash: ${result.exitCode}`);
    
    client.disconnect();
    await proc.stop();
    console.log('Core apagado.');
}

main().catch((err) => { console.error('[ERROR]', err.message); process.exit(1); });
