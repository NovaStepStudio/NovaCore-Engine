'use strict';

const path = require('path');
const CoreProcess = require('../CoreProcess');
const CoreClient = require('../CoreClient');
const {
    JAR_PATH, MC_VERSION, SHARED_DIR, INSTANCES_DIR,
    LOG_DIR, LAUNCHER_NAME, DEFAULT_AUTH, DEFAULT_WINDOW,
} = require('./config');

const INSTANCE_PATH = path.join(INSTANCES_DIR, 'vanilla-test-1');

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║   novacore-engine — Launch Vanilla       ║');
    console.log(`║   Versión: ${MC_VERSION.padEnd(30)}║`);
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
    
    client.on('debug', (d) => {
        if (process.env.VERBOSE) console.log(`[core] ${d.message}`);
    });
    
    client.on('launch_preparing', (d) => console.log(`[Launch] Preparando ${d.version}...`));
    
    client.on('launch_command_ready', (d) => {
        console.log(`[Launch] Main class: ${d.mainClass}`);
        console.log(`[Launch] Java: ${d.javaExec}`);
        console.log(`[Launch] Offline: ${d.offline}`);
        if (process.env.DEBUG_CMD) {
            console.log('\n[Launch] Comando completo:');
            d.command.forEach((arg, i) => console.log(`  [${String(i).padStart(2)}] ${arg}`));
            console.log();
        }
    });
    
    client.on('launch_started', (d) => {
        console.log('\n╔══════════════════════════════════════════╗');
        console.log('║           Minecraft en ejecución!        ║');
        console.log('╚══════════════════════════════════════════╝');
        console.log(`  Versión:  ${d.version}`);
        console.log(`  Usuario:  ${d.username}`);
        console.log(`  GameDir:  ${d.gameDir}`);
        console.log(`  Java:     ${d.javaExec}`);
        console.log(`  Offline:  ${d.offline ? '✓' : '✗'}`);
        console.log('\n─── Game Logs ──────────────────────────────');
    });
    
    client.on('launch_failed', (d) => console.error('\n✗ Launch fallido:', d.error));
    
    let logCount = 0;
    client.on('game_log', (d) => {
        logCount++;
        if (logCount <= 15 || d.line.includes('WARN') || d.line.includes('ERROR') || d.line.includes('main/INFO'))
            console.log(' >', d.line);
    });
    
    console.log('[1/1] Lanzando...\n');
    
    const { launchId } = await client.launch({
        version:      MC_VERSION,
        instancePath: INSTANCE_PATH,
        sharedPath:   SHARED_DIR,
        auth:         DEFAULT_AUTH,
        jvm:          { minMemoryMb: 0, maxMemoryMb: 0 },
        window:       DEFAULT_WINDOW,
        gcPreset:     'auto',
        hardwareAcceleration: false,
        launcher: {
            name:    LAUNCHER_NAME,
            version: '1.0.0',
        },
    });
    
    console.log(`  Launch ID: ${launchId}\n`);
    
    const result = await client.waitForGame(launchId);
    
    console.log('\n─────────────────────────────────────────────');
    console.log(`  Logs totales: ${logCount} líneas`);
    if (result.status === 'clean') console.log(`  ✓ Cerrado normalmente. Exit: ${result.exitCode}`);
    else                           console.log(`  ✗ Crash. Exit code: ${result.exitCode}`);
    
    client.disconnect();
    await proc.stop();
    console.log('Core apagado.');
}

main().catch((err) => { console.error('[ERROR]', err.message); process.exit(1); });
