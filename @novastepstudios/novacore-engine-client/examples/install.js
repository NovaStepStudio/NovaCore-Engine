'use strict';

/**
 * Ejemplo: install
 * Instala una versión de Minecraft mostrando el progreso en tiempo real.
 */

const path = require('path');
const { CoreProcess, CoreClient } = require('../src');
const config = require('./config');

const INSTANCE_PATH = path.join(config.instancesDir, `${config.mcVersion}-ejemplo`);

async function main() {
  const proc = new CoreProcess({
    jarPath:      config.jarPath,
    instancesDir: config.instancesDir,
    logDir:       config.logDir,
    launcherName: config.launcherName,
    threads:      0,
  });

  process.on('SIGINT', async () => { client.disconnect(); await proc.stop(); process.exit(0); });

  await proc.start();
  console.log('Engine listo.\n');

  const client = new CoreClient();
  await client.connect();

  // Escuchar el breakdown de tareas
  client.once('tasks_ready', (data) => {
    const b = data.breakdown;
    console.log(`Tareas: ${data.totalTasks} archivos`);
    console.log(`  client: ${b.client}, libraries: ${b.libraries}, assets: ${b.assets}, natives: ${b.natives}`);
    console.log();
  });

  // Escuchar pasos de instalación
  client.on('install_step', (data) => {
    console.log(`[step] ${data.step}`);
  });

  console.log(`Instalando Minecraft ${config.mcVersion}...`);
  console.log(`  Destino: ${INSTANCE_PATH}`);
  console.log(`  Shared:  ${config.sharedPath}\n`);

  const { sessionId } = await client.install({
    version:      config.mcVersion,
    instancePath: INSTANCE_PATH,
    sharedPath:   config.sharedPath,
    download: {
      client:    true,
      libraries: true,
      assets:    true,
      natives:   true,
      jvm:       false,
    },
    verifySHA1: true,
    maxThreads: 0,
  });

  console.log(`Sesión: ${sessionId}\n`);

  const result = await client.waitForInstall(sessionId, (snap) => {
    const done  = snap.completedFiles + snap.skippedFiles;
    const pct   = String(snap.overallPercent).padStart(3);
    const bytes = (snap.downloadedBytes / 1024 / 1024).toFixed(1);
    const total = (snap.totalBytes       / 1024 / 1024).toFixed(1);
    process.stdout.write(`\r${pct}% — ${done}/${snap.totalFiles} archivos — ${bytes}/${total} MB`);
  });

  console.log('\n');

  if (result.status === 'completed') {
    console.log('✓ Instalación completa');
    console.log(`  Descargados: ${result.completedFiles}`);
    console.log(`  Reutilizados: ${result.skippedFiles}`);
    console.log(`  Fallidos: ${result.failedFiles}`);
  } else {
    console.error('✗ Instalación fallida:', result.error);
  }

  client.disconnect();
  await proc.stop();
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
