'use strict';

/**
 * Ejemplo: full
 * Flujo completo: arrancar engine → conectar → instalar → lanzar → limpiar.
 * Es el ejemplo más representativo del uso real del cliente.
 */

const path = require('path');
const { CoreProcess, CoreClient } = require('../src');
const config = require('./config');

const INSTANCE_PATH = path.join(config.instancesDir, `${config.mcVersion}-full`);

function formatBytes(bytes) {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function main() {
  // ── 1. Arrancar el engine ──────────────────────────────────────────────────
  console.log('NovaCore-Engine Client — Ejemplo completo');
  console.log('═'.repeat(50));
  console.log('Arrancando engine...');

  const proc = new CoreProcess({
    jarPath:      config.jarPath,
    instancesDir: config.instancesDir,
    logDir:       config.logDir,
    launcherName: config.launcherName,
    threads:      0,
  });

  const client = new CoreClient();

  // Cierre limpio con Ctrl+C
  process.on('SIGINT', async () => {
    console.log('\nCerrando...');
    client.disconnect();
    await proc.stop();
    process.exit(0);
  });

  await proc.start();
  console.log(`Engine listo. PID: ${proc.pid}\n`);

  // ── 2. Conectar cliente ────────────────────────────────────────────────────
  await client.connect();
  console.log('WebSocket conectado.\n');

  // ── 3. Info del sistema ────────────────────────────────────────────────────
  const sys = await client.systemResources();
  console.log(`Sistema: ${sys.cpu.cores} cores — ${sys.ram.totalMb} MB RAM`);
  console.log(`Recomendado: ${sys.recommended.mcMaxRamMb} MB — GC: ${sys.recommended.gcPreset}\n`);

  // ── 4. Instalar ────────────────────────────────────────────────────────────
  console.log(`Instalando Minecraft ${config.mcVersion}...`);
  console.log(`  Path:   ${INSTANCE_PATH}`);
  console.log(`  Shared: ${config.sharedPath}\n`);

  client.once('tasks_ready', (data) => {
    const b = data.breakdown;
    console.log(`  Archivos: ${data.totalTasks} total`);
    console.log(`  Breakdown → client:${b.client} libs:${b.libraries} assets:${b.assets} natives:${b.natives}`);
    console.log();
  });

  const { sessionId } = await client.install({
    version:      config.mcVersion,
    instancePath: INSTANCE_PATH,
    sharedPath:   config.sharedPath,
    download: {
      client:    true,
      libraries: true,
      assets:    true,
      natives:   true,
    },
    verifySHA1: true,
    maxThreads: 0,
  });

  const installResult = await client.waitForInstall(sessionId, (snap) => {
    const done  = snap.completedFiles + snap.skippedFiles;
    const pct   = String(snap.overallPercent).padStart(3);
    const dl    = formatBytes(snap.downloadedBytes);
    const total = formatBytes(snap.totalBytes);
    process.stdout.write(`\r  ${pct}% — ${done}/${snap.totalFiles} archivos — ${dl}/${total}`);
  });

  console.log('\n');

  if (installResult.status !== 'completed') {
    console.error('Instalación fallida:', installResult.error);
    client.disconnect();
    await proc.stop();
    process.exit(1);
  }

  console.log('✓ Instalación completa');
  console.log(`  Descargados:  ${installResult.completedFiles}`);
  console.log(`  Reutilizados: ${installResult.skippedFiles}\n`);

  // ── 5. Lanzar ─────────────────────────────────────────────────────────────
  console.log('Lanzando Minecraft...\n');

  client.once('launch_started', (d) => {
    console.log(`✓ Minecraft iniciado — usuario: ${d.username} — java: ${d.javaExec}`);
    console.log('─'.repeat(50));
  });

  const { launchId } = await client.launch({
    version:      config.mcVersion,
    instancePath: INSTANCE_PATH,
    sharedPath:   config.sharedPath,
    auth: {
      username: 'StepPlayer',
      userType: 'offline',
    },
    jvm: {
      maxMemoryMb: sys.recommended.mcMaxRamMb,
    },
    gcPreset: sys.recommended.gcPreset,
    launcher: {
      name:    config.launcherName,
      version: '1.0.0',
    },
  });

  const unsub = client.onGameLog(launchId, (line) => {
    process.stdout.write(line + '\n');
  });

  const exitResult = await client.waitForGame(launchId);
  unsub();

  console.log('─'.repeat(50));
  if (exitResult.status === 'crash') {
    console.error(`✗ Minecraft crasheó (exit code ${exitResult.exitCode})`);
  } else {
    console.log('✓ Minecraft cerró normalmente');
  }

  // ── 6. Limpiar ─────────────────────────────────────────────────────────────
  client.disconnect();
  await proc.stop();
  console.log('\nEngine detenido. ¡Hasta la próxima!');
}

main().catch((err) => {
  console.error('\nError fatal:', err.message);
  process.exit(1);
});
