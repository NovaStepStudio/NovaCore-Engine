'use strict';

const path        = require('path');
const CoreProcess = require('../CoreProcess');
const { CoreClient }  = require('../CoreClient');
const { JAR_PATH, MC_VERSION, SHARED_DIR, INSTANCES_DIR, LOG_DIR, LAUNCHER_NAME } = require('./config');

const FABRIC_INSTANCE    = path.join(INSTANCES_DIR);
const NEOFORGE_INSTANCE  = path.join(INSTANCES_DIR);

function fmt(bytes) {
    if (!bytes) return '0 B';
    const u = ['B', 'KB', 'MB', 'GB'];
    let i = 0;
    while (bytes >= 1024 && i < u.length - 1) { bytes /= 1024; i++; }
    return `${bytes.toFixed(1)} ${u[i]}`;
}

function bar(pct, w = 24) {
    const f = Math.round((pct / 100) * w);
    return '[' + '█'.repeat(f) + '░'.repeat(w - f) + ']';
}

async function installModLoader(client, loaderName, loaderVersion, instancePath) {
    console.log(`\n══ ${loaderName.toUpperCase()} ═══════════════════════════════════════`);

    console.log(`  [1/2] Instalando Minecraft ${MC_VERSION}...`);

    const unsubProg = client.onEvent('session_progress', (d) => {
        process.stdout.write(
            `\r       ${bar(d.percent)} ${String(d.percent).padStart(3)}%` +
            ` | ${(d.completedFiles ?? 0) + (d.skippedFiles ?? 0)}/${d.totalFiles}` +
            ` | ${fmt(d.downloadedBytes)}   `,
        );
    });

    const { sessionId: mcSession } = await client.install({
        version:      MC_VERSION,
        instancePath,
        sharedPath:   SHARED_DIR,
        download:     { client: true, libraries: true, assets: true, natives: true, jvm: true },
        verifySHA1:   true,
    });

    const mcResult = await client.waitForInstall(mcSession);
    unsubProg();
    console.log(`\n       ${mcResult.status === 'completed' ? '✓' : '✗'} Minecraft` +
        ` (${mcResult.completedFiles} new, ${mcResult.skippedFiles} reused)`);

    if (mcResult.status !== 'completed') {
        console.error('  Minecraft install failed:', mcResult.error);
        return;
    }

    const label = loaderVersion ?? 'latest';
    console.log(`\n  [2/2] Instalando ${loaderName} ${label}...`);

    let downloadedFiles = 0;

    const unsubResolvng = client.onEvent('modloader_resolving', (d) => {
        if (d.sessionId !== mlSession) return;
        console.log(`       Resolviendo ${d.loader} para MC ${d.mcVersion}...`);
    });

    const { sessionId: mlSession } = await client.installModLoader({
        loader:           loaderName,
        loaderVersion,
        minecraftVersion: MC_VERSION,
        instancePath,
        sharedPath:       SHARED_DIR,
    });

    console.log(`       Session: ${mlSession}`);

    const unsubMlDownload = client.onEvent('modloader_downloading', (d) => {
        if (d.sessionId !== mlSession) return;
        downloadedFiles = d.files;
        process.stdout.write(`\r       Descargando ${d.files} archivo(s)...   `);
    });

    const result = await client.waitForModLoader(mlSession);
    unsubResolvng();
    unsubMlDownload();

    console.log(`\n       ✓ ${result.loader} ${result.loaderVersion} instalado`);
    console.log(`         versionJsonId: ${result.versionJsonId}`);
    if (downloadedFiles > 0) console.log(`         Archivos descargados: ${downloadedFiles}`);

    try {
        const state = await client.getModLoaderState(instancePath);
        console.log(`\n       Estado guardado:`);
        console.log(`         loader:      ${state.loaderType}`);
        console.log(`         version:     ${state.loaderVersion}`);
        console.log(`         mc:          ${state.minecraftVersion}`);
        console.log(`         installedAt: ${new Date(state.installedAt).toLocaleString()}`);
    } catch (_) {
        console.log('       (estado no disponible via HTTP — verificar log)');
    }
}

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║    novacore-engine — ModLoader Install    ║');
    console.log(`║    MC ${MC_VERSION.padEnd(35)}║`);
    console.log('╚══════════════════════════════════════════╝\n');

    const proc = new CoreProcess({
        jarPath:      JAR_PATH,
        instancesDir: INSTANCES_DIR,
        logDir:       LOG_DIR,
        launcherName: LAUNCHER_NAME,
        threads:      0,
        verbose:      false,
    });

    process.on('SIGINT', async () => {
        console.log('\n[!] Interrumpido.');
        client?.disconnect();
        await proc.stop();
        process.exit(0);
    });

    process.stdout.write('Iniciando core... ');
    await proc.start();
    proc.on('stderr', (line) => { if (!line.includes('WARNING')) console.error('[Java]', line); });
    console.log(`PID: ${proc.pid} ✓\n`);

    let client;
    client = new CoreClient({ accessToken: proc.accessToken });
    await client.connect();

    const { loaders } = await client.listModLoaders();
    console.log(`Loaders soportados (${loaders.length}): ${loaders.join(', ')}\n`);

    console.log(`Versiones disponibles para MC ${MC_VERSION}:\n`);

    for (const loader of ['forge','optifine','quilt','fabric', 'neoforge']) {
        try {
            const { versions } = await client.getModLoaderVersions(loader, MC_VERSION);
            const stable = versions.filter((v) => v.stable);
            const latest = stable[0] ?? versions[0];
            console.log(`  ${loader.padEnd(12)}: ${versions.length} versiones` +
                ` | último stable: ${latest?.loaderVersion ?? 'n/a'}`);
            if (stable.length > 0 && stable.length < versions.length) {
                console.log(`               (${stable.length} stable, ${versions.length - stable.length} unstable)`);
            }
        } catch (err) {
            console.log(`  ${loader.padEnd(12)}: Error al consultar — ${err.message}`);
        }
    }

    // await installModLoader(client, 'fabric', null, FABRIC_INSTANCE);
    const loadersToInstall = [
        'forge',
        'fabric',
        'quilt',
        'neoforge',
        'optifine'
    ];

    for (const loader of loadersToInstall) {
        try {
            await installModLoader(client, loader, null, INSTANCES_DIR);
        } catch (err) {
            console.error(`\nError instalando ${loader}:`, err.message);
        }
    }

    client.disconnect();
    await proc.stop();
    console.log('\nCore apagado.');
}

main().catch((err) => { console.error('\n[FATAL]', err.message); process.exit(1); });
