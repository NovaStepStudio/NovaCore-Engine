'use strict';

const path = require('path');
const CoreProcess = require('../CoreProcess');
const { CoreClient } = require('../CoreClient');
const { JAR_PATH, MC_VERSION, SHARED_DIR, INSTANCES_DIR, LOG_DIR, LAUNCHER_NAME } = require('./config');

function bar(pct, w = 28) {
    const f = Math.round((pct / 100) * w);
    return '[' + '█'.repeat(f) + '░'.repeat(w - f) + ']';
}
function fmt(bytes) {
    if (!bytes) return '0 B';
    const u = ['B','KB','MB','GB']; let i = 0;
    while (bytes >= 1024 && i < u.length - 1) { bytes /= 1024; i++; }
    return `${bytes.toFixed(1)} ${u[i]}`;
}

async function installVersion(client, label, instancePath, sharedPath, version, downloadJvm = false) {
    console.log(`\n── Instalando "${label}" ────────────────────`);
    console.log(`   Versión:   ${version}`);
    console.log(`   Instancia: ${instancePath}`);
    console.log(`   Shared:    ${sharedPath || '(none)'}\n`);
    
    const start = Date.now();
    
    const unsubProg = client.onEvent('session_progress', (d) => {
        process.stdout.write(
            `\r  ${bar(d.percent)} ${String(d.percent).padStart(3)}%` +
            ` | ${(d.completedFiles ?? 0) + (d.skippedFiles ?? 0)}/${d.totalFiles}` +
            ` | ${fmt(d.downloadedBytes)}   `
        );
    });
    
    client.once('tasks_ready', (d) => {
        const b = d.breakdown;
        console.log(`  Tareas: ${d.totalTasks}  →  C:${b.client} L:${b.libraries} A:${b.assets} N:${b.natives}`);
        if (b.asset_index) console.log(`  Asset index: ${b.asset_index}`);
        console.log();
    });
    
    const { sessionId } = await client.install({
        version,
        instancePath,
        sharedPath,
        download: { client: true, libraries: true, assets: true, natives: true, jvm: downloadJvm },
        verifySHA1: true,
    });
    
    console.log(`  Session: ${sessionId}`);
    const result = await client.waitForInstall(sessionId);
    unsubProg();
    
    const elapsed = ((Date.now() - start) / 1000).toFixed(1);
    console.log('\n');
    
    if (result.status === 'completed') {
        console.log(`  ✓ "${label}" instalado en ${elapsed}s`);
        console.log(`    Nuevos:         ${result.completedFiles}`);
        console.log(`    Reutilizados:   ${result.skippedFiles}`);
        console.log(`    Descargado:     ${fmt(result.downloadedBytes)}`);
    } else {
        console.log(`  ✗ Fallido: ${result.error}`);
    }
    return result;
}

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║  novacore-engine — Install (Shared)      ║');
    console.log('╚══════════════════════════════════════════╝');
    console.log(`\nVersión:    ${MC_VERSION}`);
    console.log(`Shared:     ${SHARED_DIR}`);
    console.log(`Instancias: ${INSTANCES_DIR}\n`);
    
    const proc = new CoreProcess({
        jarPath:      JAR_PATH,
        instancesDir: INSTANCES_DIR,
        logDir:       LOG_DIR,
        launcherName: LAUNCHER_NAME,
        threads:      0,
        verbose:      false,
    });
    process.on('SIGINT', async () => { await proc.stop(); process.exit(0); });
    await proc.start();
    proc.on('stderr', (line) => { if (!line.includes('WARNING')) console.error('[Java]', line); });
    
    const client = new CoreClient({ accessToken: proc.accessToken });
    await client.connect();
    
    client.on('debug', (d) => {
        if (d.message.includes('Natives') || d.message.includes('JVM') || d.message.includes('Shared'))
            console.log(`  [core] ${d.message}`);
    });
    
    client.on('runtime_download_start',    (d) =>
        console.log(`\n  [JVM] Descargando Java ${d.javaVersion} (${d.totalFiles} archivos)...`));
    client.on('runtime_download_complete', (d) =>
        console.log(`  [JVM] ✓ Java ${d.javaVersion} listo\n`));

    console.log('══ PRIMERA INSTALACIÓN (descarga todo) ════\n');
    const inst1 = path.join(INSTANCES_DIR);
await installVersion(client, 'Vanilla ' + MC_VERSION, inst1, SHARED_DIR, MC_VERSION, true);

console.log('\n══ SEGUNDA INSTALACIÓN (reutiliza shared) ═');
console.log('   (Libs/assets ya en shared → skip inmediato)\n');

console.log('\n══ Resumen de sesiones ════════════════════\n');
const sessions = await client.allSessions();
for (const s of sessions.sessions) {
    console.log(`  Session: ${s.sessionId}`);
    console.log(`    Estado:       ${s.status}`);
    console.log(`    Nuevos:       ${s.completedFiles}`);
    console.log(`    Reutilizados: ${s.skippedFiles}`);
    console.log(`    Descargado:   ${fmt(s.downloadedBytes)} / ${fmt(s.totalBytes)}`);
    console.log();
}

client.disconnect();
await proc.stop();
console.log('Core apagado.');
}

main().catch((err) => { console.error('[ERROR]', err.message); process.exit(1); });
