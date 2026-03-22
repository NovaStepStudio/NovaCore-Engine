'use strict';

/**
 * Ejemplo: advanced
 * Lanzamiento avanzado con todas las opciones: MSA auth, GPU, GC, authlib-injector, etc.
 * Este ejemplo muestra la API completa. Ajustá las opciones según tus necesidades.
 */

const path = require('path');
const { CoreProcess, CoreClient } = require('../src');
const config = require('./config');

const INSTANCE_PATH = path.join(config.instancesDir, `${config.mcVersion}-advanced`);

async function main() {
  const proc = new CoreProcess({
    jarPath:      config.jarPath,
    instancesDir: config.instancesDir,
    logDir:       config.logDir,
    launcherName: config.launcherName,
    threads:      0,
    logLevel:     'DEBUG',
    verbose:      true, // imprime stdout del engine
  });

  proc.on('log',    (l) => console.log(`[engine] ${l}`));
  proc.on('stderr', (l) => console.error(`[engine:err] ${l}`));

  process.on('SIGINT', async () => { client.disconnect(); await proc.stop(); process.exit(0); });

  await proc.start();
  console.log('Engine listo.\n');

  const client = new CoreClient();
  await client.connect();

  // Escuchar TODOS los eventos del WebSocket
  client.on('*', (event, data) => {
    if (event === 'game_log') return; // no spammear los logs del juego acá
    console.log(`[ws:${event}]`, JSON.stringify(data).slice(0, 120));
  });

  // ── Instalar primero ───────────────────────────────────────────────────────
  console.log('Instalando (con JVM de Mojang)...\n');
  const { sessionId } = await client.install({
    version:      config.mcVersion,
    instancePath: INSTANCE_PATH,
    sharedPath:   config.sharedPath,
    download: {
      client:    true,
      libraries: true,
      assets:    true,
      natives:   true,
      jvm:       true, // descarga el JVM de Mojang
    },
    verifySHA1: true,
    maxThreads: 0,
    debug:      true,
  });

  const result = await client.waitForInstall(sessionId, (snap) => {
    process.stdout.write(`\r${snap.overallPercent}%`);
  });
  console.log(`\nInstalación: ${result.status}`);
  if (result.status === 'failed') process.exit(1);

  // ── Consultar recursos para elegir config ──────────────────────────────────
  const sys = await client.systemResources();
  console.log(`\nRAM recomendada: ${sys.recommended.mcMaxRamMb} MB — GC: ${sys.recommended.gcPreset}\n`);

  // ── Lanzamiento avanzado ───────────────────────────────────────────────────
  const { launchId } = await client.launch({
    version:      config.mcVersion,
    instancePath: INSTANCE_PATH,
    sharedPath:   config.sharedPath,

    // Auth — para MSA real, reemplazá con los tokens de tu flow de autenticación
    auth: {
      username:    'AdvancedPlayer',
      uuid:        '00000000-0000-0000-0000-000000000001',
      accessToken: '0',
      userType:    'offline',
    },

    // JVM
    jvm: {
      minMemoryMb: 512,
      maxMemoryMb: sys.recommended.mcMaxRamMb,
      extraArgs:   [
        '-XX:+UnlockExperimentalVMOptions',
        '-Dfml.ignoreInvalidMinecraftCertificates=true',
      ],
    },

    // GC desde las recomendaciones del sistema
    gcPreset: sys.recommended.gcPreset,

    // GPU dedicada (en laptops con dual GPU)
    gpuPreference:        'dgpu',
    hardwareAcceleration: true,

    // Ventana
    window: { width: 1920, height: 1080, fullscreen: false },

    // Branding
    launcher: { name: config.launcherName, version: '1.0.0' },

    // Personalización del juego
    game: {
      extraGameArgs: ['--demo'],
    },
  });

  console.log(`\nlaunchId: ${launchId}`);
  console.log('─'.repeat(60));

  const unsub = client.onGameLog(launchId, (line) => {
    // Filtrar solo errores y warnings para no saturar la consola
    if (line.includes('ERROR') || line.includes('FATAL') || line.includes('WARN')) {
      console.log(`[MC] ${line}`);
    }
  });

  const exit = await client.waitForGame(launchId);
  unsub();

  console.log('─'.repeat(60));
  console.log(`Juego cerrado — status: ${exit.status} (exit code ${exit.exitCode})`);

  client.disconnect();
  await proc.stop();
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
