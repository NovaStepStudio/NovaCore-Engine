'use strict';

/**
 * Ejemplo: sysinfo
 * Muestra info del sistema y lista las versiones de Minecraft disponibles.
 */

const { CoreProcess, CoreClient } = require('../src');
const config = require('./config');

async function main() {
  const proc = new CoreProcess({
    jarPath:      config.jarPath,
    instancesDir: config.instancesDir,
    logDir:       config.logDir,
    launcherName: config.launcherName,
    threads:      0,
  });

  process.on('SIGINT', async () => { await proc.stop(); process.exit(0); });

  console.log('Arrancando NovaCore-Engine...');
  await proc.start();
  console.log(`Engine listo. PID: ${proc.pid}\n`);

  const client = new CoreClient();

  // ── Info de la API ────────────────────────────────────────────────────────
  const info = await client.apiInfo();
  console.log('=== API Info ===');
  console.log(`  Nombre:  ${info.name} v${info.version}`);
  console.log(`  Vendor:  ${info.vendor}`);
  console.log(`  Java:    ${info.java}`);
  console.log(`  OS:      ${info.os}`);
  console.log();

  // ── Recursos del sistema ──────────────────────────────────────────────────
  const sys = await client.systemResources();
  console.log('=== Sistema ===');
  console.log(`  CPU cores:         ${sys.cpu.cores}`);
  console.log(`  Threads óptimos:   ${sys.cpu.optimalDlThreads}`);
  console.log(`  RAM total:         ${sys.ram.totalMb} MB`);
  console.log(`  RAM libre aprox:   ${sys.ram.estimatedFreeMb} MB`);
  console.log(`  RAM recomendada:   ${sys.recommended.mcMinRamMb}-${sys.recommended.mcMaxRamMb} MB`);
  console.log(`  GC recomendado:    ${sys.recommended.gcPreset}`);
  console.log();

  // ── Versiones ─────────────────────────────────────────────────────────────
  const releases = await client.versions('release');
  console.log(`=== Versiones release (${releases.count}) ===`);
  console.log(`  Última release:   ${releases.latest.release}`);
  console.log(`  Último snapshot:  ${releases.latest.snapshot}`);
  console.log('  Últimas 5 releases:');
  releases.versions.slice(0, 5).forEach((v) => {
    console.log(`    ${v.id.padEnd(12)} — ${v.releaseTime.slice(0, 10)}`);
  });

  await proc.stop();
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
