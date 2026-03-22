'use strict';

/**
 * Ejemplo: launch
 * Lanza Minecraft en modo offline y muestra los logs en consola.
 * Requiere que la versión ya esté instalada (corré npm run example:install primero).
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
  });

  process.on('SIGINT', async () => { client.disconnect(); await proc.stop(); process.exit(0); });

  await proc.start();
  console.log('Engine listo.\n');

  const client = new CoreClient();
  await client.connect();

  client.once('launch_preparing',     (d) => console.log(`[event] launch_preparing → ${d.version}`));
  client.once('launch_command_ready', (d) => console.log(`[event] launch_command_ready → mainClass: ${d.mainClass}`));
  client.once('launch_started',       (d) => console.log(`[event] launch_started → usuario: ${d.username}\n`));
  client.once('launch_failed',        (d) => {
    console.error(`[event] launch_failed → ${d.error}`);
    process.exit(1);
  });

  console.log(`Lanzando Minecraft ${config.mcVersion} en modo offline...`);
  console.log(`  Path: ${INSTANCE_PATH}\n`);

  const { launchId } = await client.launch({
    version:      config.mcVersion,
    instancePath: INSTANCE_PATH,
    sharedPath:   config.sharedPath,
    auth: {
      username: 'StepPlayer',
      userType: 'offline',
    },
    jvm: {
      minMemoryMb: 512,
      maxMemoryMb: 2048,
    },
    gcPreset: 'g1gc_optimized',
    window: {
      width:  1280,
      height: 720,
    },
    launcher: {
      name:    config.launcherName,
      version: '1.0.0',
    },
  });

  console.log(`launchId: ${launchId}`);
  console.log('─'.repeat(60));

  const unsub = client.onGameLog(launchId, (line) => {
    process.stdout.write(line + '\n');
  });

  const result = await client.waitForGame(launchId);
  unsub();

  console.log('─'.repeat(60));
  if (result.status === 'crash') {
    console.error(`✗ Minecraft crasheó (exit code ${result.exitCode})`);
  } else {
    console.log('✓ Minecraft cerró normalmente');
  }

  client.disconnect();
  await proc.stop();
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
