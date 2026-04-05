'use strict';

const path = require('path');
const fs = require('fs');
const CoreProcess = require('../CoreProcess');
const { CoreClient } = require('../CoreClient');
const {
    JAR_PATH, MC_VERSION, SHARED_DIR, INSTANCES_DIR,
    LOG_DIR, LAUNCHER_NAME, DEFAULT_AUTH,
} = require('./config');

function bar(pct, w = 24) {
    const f = Math.round((pct / 100) * w);
    return '[' + '█'.repeat(f) + '░'.repeat(w - f) + ']';
}
function fmt(bytes) {
    if (!bytes) return '0 B';
    const u = ['B','KB','MB','GB']; let i = 0;
    while (bytes >= 1024 && i < u.length - 1) { bytes /= 1024; i++; }
    return `${bytes.toFixed(1)} ${u[i]}`;
}
function fmtMb(mb) {
    return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`;
}

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║       StepLauncher — novacore-engine      ║');
    console.log(`║       Versión: ${MC_VERSION.padEnd(26)}║`);
    console.log('╚══════════════════════════════════════════╝\n');
    
    const proc = new CoreProcess({
        jarPath:      JAR_PATH,
        instancesDir: INSTANCES_DIR,
        logDir:       LOG_DIR,
        launcherName: LAUNCHER_NAME,
        logLevel:     'INFO',
        verbose:      false,
        threads:      0,
    });
    
    let client;
    
    process.on('SIGINT', async () => {
        console.log('\n[!] Interrumpido.');
        client?.disconnect();
        await proc.stop();
        process.exit(0);
    });
    
    process.stdout.write('Iniciando core...');
    await proc.start();
    proc.on('stderr', (line) => { if (!line.includes('WARNING')) console.error('[Java]', line); });
    console.log(` PID: ${proc.pid} ✓\n`);
    
    client = new CoreClient({ accessToken: proc.accessToken });
    await client.connect();
    
    // ── Recursos del sistema
    console.log('Consultando recursos del sistema...');
    const resources    = await client.systemResources();
    const { recommended } = resources;
    
    console.log(`  CPU: ${resources.cpu.cores} cores → ${recommended.downloadThreads} threads`);
    console.log(`  RAM: ${fmtMb(resources.ram.totalMb)} total → MC: ${fmtMb(recommended.mcMinRamMb)}-${fmtMb(recommended.mcMaxRamMb)}`);
    console.log(`  GC recomendado: ${recommended.gcPreset}\n`);
    
    // ── Resolver instancia
    console.log('Resolviendo instancia...');
    const instanceName = `${MC_VERSION}-vanilla`;
    const instancePath = path.join(INSTANCES_DIR, instanceName);
    
    const allInstances = await client.listInstances();
    let instance = allInstances.instances.find((i) => i.name === instanceName);
    
    if (!instance) {
        console.log(`  Instancia "${instanceName}" no existe, creando...`);
        instance = await client.createInstance({
            name:      instanceName,
            mcVersion: MC_VERSION,
            config: {
                modLoader:      'vanilla',
                minMemoryMb:    recommended.mcMinRamMb,
                maxMemoryMb:    recommended.mcMaxRamMb,
                gcPreset:       recommended.gcPreset,
                launcherName:   LAUNCHER_NAME,
                launcherVersion: '1.0.0',
            },
        });
        console.log(`  ✓ Creada: ${instance.id}`);
    } else {
        console.log(`  ✓ Cargada: ${instance.id}`);
    }
    console.log(`  Path: ${instance.path || instancePath}\n`);
    
    // ── Verificar instalación
    const clientJar   = path.join(instancePath, 'versions', MC_VERSION, `${MC_VERSION}.jar`);
    const isInstalled = fs.existsSync(clientJar);
    console.log(`Estado: ${isInstalled ? '✓ Instalada' : '○ Requiere instalación'}\n`);
    
    // ── Instalar si hace falta
    if (!isInstalled) {
        console.log('══ Instalando ══════════════════════════════\n');
        
        client.once('tasks_ready', (d) => {
            const b = d.breakdown;
            console.log(`  ${d.totalTasks} archivos → C:${b.client} L:${b.libraries} A:${b.assets} N:${b.natives}\n`);
        });
        
        client.on('runtime_download_start',    (d) =>
            console.log(`\n  [JVM] Descargando Java ${d.javaVersion} (${d.totalFiles} archivos)...`));
        client.on('runtime_download_complete', (d) =>
            console.log(`  [JVM] ✓ Java ${d.javaVersion} listo\n`));

        let lastPct = -1;
const unsubProg = client.onEvent('session_progress', (d) => {
    if (d.percent === lastPct) return;
    lastPct = d.percent;
    process.stdout.write(
        `\r  ${bar(d.percent)} ${String(d.percent).padStart(3)}%` +
        ` | ${(d.completedFiles ?? 0) + (d.skippedFiles ?? 0)}/${d.totalFiles}` +
        ` | ${fmt(d.downloadedBytes)}   `
    );
});

const { sessionId } = await client.install({
    version:      MC_VERSION,
    instancePath: instancePath,
    sharedPath:   SHARED_DIR,
    download: {
        client:    true,
        libraries: true,
        assets:    true,
        natives:   true,
        jvm:       true,
    },
    maxThreads: recommended.downloadThreads,
    verifySHA1: true,
});

const result = await client.waitForInstall(sessionId);
unsubProg();
client.removeAllListeners('session_progress');
client.removeAllListeners('runtime_download_start');
client.removeAllListeners('runtime_download_complete');
console.log('\n');

if (result.status !== 'completed')
    throw new Error('Instalación fallida: ' + result.error);

console.log(`  ✓ Instalado: ${result.completedFiles} nuevos, ${result.skippedFiles} reutilizados`);
console.log(`  Descargado: ${fmt(result.downloadedBytes)}\n`);
}

// ── Lanzar
console.log('══ Lanzando ════════════════════════════════\n');

const launchStart = Date.now();

client.on('debug', (d) => {
    if (d.message.includes('GC') || d.message.includes('Java') || d.message.includes('Offline'))
        console.log(`  [core] ${d.message}`);
});

client.on('launch_started', (d) => {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║           Minecraft en ejecución!        ║');
    console.log('╚══════════════════════════════════════════╝');
    console.log(`  Usuario: ${d.username}`);
    console.log(`  Java:    ${d.javaExec}`);
    console.log(`  Offline: ${d.offline}`);
    console.log('\n─── Logs ──────────────────────────────────');
});

client.on('launch_failed', (d) => {
    throw new Error('Launch fallido: ' + d.error);
});

const filteredPatterns = [/\[main\/INFO\]/, /\[Render thread\/INFO\]/, /Exception/, /Error/i, /WARN/];
let totalLogs = 0;
client.on('game_log', (d) => {
    totalLogs++;
    if (totalLogs <= 8 || filteredPatterns.some((p) => p.test(d.line)))
        console.log(' >', d.line);
});

const { launchId } = await client.launch({
    version:      MC_VERSION,
    instancePath: instancePath,
    sharedPath:   SHARED_DIR,
    auth:         DEFAULT_AUTH,
    
    jvm: {
        minMemoryMb: recommended.mcMinRamMb,
        maxMemoryMb: recommended.mcMaxRamMb,
    },
    
    gcPreset:   recommended.gcPreset,
    window:     { width: 1280, height: 720 },
    
    launcher: {
        name:    LAUNCHER_NAME,
        version: '1.0.0',
    },
    
    hardwareAcceleration: false,
    gpuPreference:        'auto',
});

console.log(`\n  Launch ID: ${launchId}`);

// ── Esperar cierre
const result   = await client.waitForGame(launchId);
const playTime = Date.now() - launchStart;

console.log('\n─────────────────────────────────────────────');
console.log(result.status === 'clean'
    ? `  ✓ Juego cerrado. Exit: ${result.exitCode}`
    : `  ✗ Crash. Exit: ${result.exitCode}`
);
console.log(`  Tiempo de juego: ${(playTime / 60000).toFixed(1)} min`);
console.log(`  Logs mostrados: ${totalLogs} líneas`);

// ── Registrar tiempo de juego
if (instance?.id) {
    try {
        await client._post(`/instances/${instance.id}/playtime`, { durationMs: playTime });
    } catch (_) {}
}

client.disconnect();
await proc.stop();
console.log('\nCore apagado.');
}

main().catch((err) => { console.error('\n[FATAL]', err.message); process.exit(1); });
