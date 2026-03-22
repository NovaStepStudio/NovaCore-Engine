'use strict';

const CoreProcess = require('../CoreProcess');
const CoreClient = require('../CoreClient');
const { JAR_PATH, INSTANCES_DIR, LOG_DIR, LAUNCHER_NAME } = require('./config');

function fmtMb(mb) {
    if (!mb) return '0 MB';
    return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb} MB`;
}

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║     novacore-engine — System Info        ║');
    console.log('╚══════════════════════════════════════════╝\n');
    
    const proc = new CoreProcess({
        jarPath:      JAR_PATH,
        instancesDir: INSTANCES_DIR,
        logDir:       LOG_DIR,
        launcherName: LAUNCHER_NAME,
    });
    await proc.start();
    proc.on('stderr', (line) => { if (!line.includes('WARNING')) console.error('[Java]', line); });
    console.log(`Core iniciado. PID: ${proc.pid}\n`);
    
    const client = new CoreClient();
    await client.connect();
    
    const api = await client.apiInfo();
    console.log('══ API Info ══════════════════════════════');
    console.log(`  Name:    ${api.name}`);
    console.log(`  Vendor:  ${api.vendor}`);
    console.log(`  Version: ${api.version}`);
    console.log(`  OS:      ${api.os}`);
    console.log(`  Java:    ${api.java}`);
    console.log('\n  Endpoints:');
    for (const [k, v] of Object.entries(api.endpoints)) {
        if (typeof v === 'string') console.log(`    ${k.padEnd(18)} → ${v}`);
        else for (const [sk, sp] of Object.entries(v)) console.log(`    ${(k+'.'+sk).padEnd(18)} → ${sp}`);
    }
    
    const res = await client.systemResources();
    console.log('\n══ Recursos del Sistema ═══════════════════');
    console.log(`  CPU cores:          ${res.cpu.cores}`);
    console.log(`  Threads óptimos:    ${res.cpu.optimalDlThreads}`);
    console.log(`  RAM total:          ${fmtMb(res.ram.totalMb)}`);
    console.log(`  RAM libre:          ${fmtMb(res.ram.estimatedFreeMb)}`);
    console.log(`  Reservada OS:       ${fmtMb(res.ram.reservedForOsMb)}`);
    console.log(`  MC min RAM:         ${fmtMb(res.recommended.mcMinRamMb)}`);
    console.log(`  MC max RAM:         ${fmtMb(res.recommended.mcMaxRamMb)}`);
    console.log(`  GC recomendado:     ${res.recommended.gcPreset}`);
    
    const releases  = await client.versions('release');
    const snapshots = await client.versions('snapshot');
    console.log('\n══ Versiones ══════════════════════════════');
    console.log(`  Releases:   ${releases.count}`);
    console.log(`  Snapshots:  ${snapshots.count}`);
    console.log(`  Último release:   ${releases.latest.release}`);
    console.log(`  Último snapshot:  ${releases.latest.snapshot}`);
    console.log('\n  Últimos 5 releases:');
    releases.versions.slice(0, 5).forEach((v) => {
        const date = new Date(v.releaseTime).toLocaleDateString();
        console.log(`    ${v.id.padEnd(12)} (${date})`);
    });
    
    client.disconnect();
    await proc.stop();
    console.log('\nCore apagado.');
}

main().catch((err) => { console.error('[ERROR]', err.message); process.exit(1); });
