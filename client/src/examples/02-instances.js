'use strict';

const CoreProcess = require('../CoreProcess');
const CoreClient = require('../CoreClient');
const { JAR_PATH, MC_VERSION, INSTANCES_DIR, SHARED_DIR, LOG_DIR, LAUNCHER_NAME } = require('./config');

async function main() {
    console.log('╔══════════════════════════════════════════╗');
    console.log('║     novacore-engine — Instances          ║');
    console.log('╚══════════════════════════════════════════╝\n');
    
    const proc = new CoreProcess({
        jarPath:      JAR_PATH,
        instancesDir: INSTANCES_DIR,
        logDir:       LOG_DIR,
        launcherName: LAUNCHER_NAME,
    });
    await proc.start();
    proc.on('stderr', (line) => { if (!line.includes('WARNING')) console.error('[Java]', line); });
    
    const client = new CoreClient();
    await client.connect();
    
    console.log('══ Crear instancia (sin auto-install) ═════\n');
    
    const vanilla = await client.createInstance({
        name:      'Vanilla 1.21.1',
        mcVersion: MC_VERSION,
        config: {
            modLoader:   'vanilla',
            minMemoryMb: 512,
            maxMemoryMb: 2048,
            gcPreset:    'auto',
        },
    });
    console.log(`✓ Creada: "${vanilla.name}"  ID: ${vanilla.id}`);
    console.log(`  Path: ${vanilla.path}\n`);
    
    console.log('══ Crear instancia con auto-install ════════\n');
    console.log('  (Esto crea la instancia Y lanza la instalación en background)\n');
    
    client.on('session_progress', (d) => {
        process.stdout.write(
            `\r  Instalando... ${String(d.percent).padStart(3)}% | ` +
            `${(d.completedFiles ?? 0) + (d.skippedFiles ?? 0)}/${d.totalFiles}   `
        );
    });
    
    const autoInst = await client.createInstance({
        name:        'Vanilla Auto',
        mcVersion:   MC_VERSION,
        config:      { modLoader: 'vanilla', maxMemoryMb: 2048 },
        autoInstall: true,
        install: {
            sharedPath: SHARED_DIR,
            download:   { client: true, libraries: true, assets: true, natives: true, jvm: false },
            verifySHA1: true,
        },
    });
    
    console.log(`✓ Creada: "${autoInst.name}"  ID: ${autoInst.id}`);
    if (autoInst.installSessionId) {
        console.log(`  Instalación iniciada. Session: ${autoInst.installSessionId}`);
        const result = await client.waitForInstall(autoInst.installSessionId);
        console.log(`\n  Estado final: ${result.status}`);
        console.log(`  Nuevos: ${result.completedFiles}  Reutilizados: ${result.skippedFiles}`);
    }
    
    client.removeAllListeners('session_progress');
    
    console.log('\n══ Listar instancias ══════════════════════\n');
    const list = await client.listInstances();
    console.log(`Total: ${list.count} instancias`);
    for (const inst of list.instances) {
        const status = inst.installed ? '✓' : '○';
        console.log(`  [${status}] ${inst.name}  (${inst.mcVersion})  ${inst.path}`);
    }
    
    console.log('\n══ Actualizar instancia ═══════════════════\n');
    const updated = await client.updateInstance(vanilla.id, {
        maxMemoryMb: 3072,
        gcPreset:    'g1gc_optimized',
        jvmArgs:     ['-XX:+UnlockExperimentalVMOptions'],
        launcherName: 'StepLauncher',
        launcherVersion: '1.0.0',
    });
    console.log(`✓ Actualizada: ${updated.id}`);
    
    console.log('\n══ Obtener path ═══════════════════════════\n');
    const pathInfo = await client.getInstancePath(vanilla.id);
    console.log(`Path: ${pathInfo.path}`);
    
    console.log('\n══ Eliminar instancia de prueba ═══════════\n');
    const del1 = await client.deleteInstance(vanilla.id);
    const del2 = await client.deleteInstance(autoInst.id);
    console.log(`Eliminadas: ${del1.id}, ${del2.id}`);
    
    const finalList = await client.listInstances();
    console.log(`\nInstancias restantes: ${finalList.count}`);
    
    client.disconnect();
    await proc.stop();
    console.log('\nCore apagado.');
}

main().catch((err) => { console.error('[ERROR]', err.message); process.exit(1); });
