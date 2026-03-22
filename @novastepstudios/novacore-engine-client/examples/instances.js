'use strict';

/**
 * Ejemplo: instances
 * Crea, lista, actualiza y borra instancias.
 */

const { CoreProcess, CoreClient } = require('../src');
const config = require('./config');

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

  // ── Crear instancia ───────────────────────────────────────────────────────
  console.log(`Creando instancia "${config.mcVersion}-vanilla"...`);
  const inst = await client.createInstance({
    name:      `${config.mcVersion}-vanilla`,
    mcVersion: config.mcVersion,
    config: {
      modLoader:    'vanilla',
      minMemoryMb:  512,
      maxMemoryMb:  4096,
      gcPreset:     'g1gc_optimized',
      launcherName: config.launcherName,
    },
    autoInstall: false,
  });
  console.log(`  ID:   ${inst.id}`);
  console.log(`  Path: ${inst.path}\n`);

  // ── Listar instancias ─────────────────────────────────────────────────────
  const { count, instances } = await client.listInstances();
  console.log(`Instancias registradas: ${count}`);
  instances.forEach((i) => {
    const status = i.installed ? '✓ instalada' : '○ sin instalar';
    console.log(`  [${status}] ${i.name} (${i.mcVersion}) — RAM: ${i.minMemoryMb}-${i.maxMemoryMb} MB`);
  });
  console.log();

  // ── Actualizar ────────────────────────────────────────────────────────────
  console.log('Actualizando maxMemoryMb a 8192...');
  await client.updateInstance(inst.id, { maxMemoryMb: 8192 });
  const updated = await client.getInstance(inst.id);
  console.log(`  RAM actualizada: ${updated.maxMemoryMb} MB\n`);

  // ── Borrar ────────────────────────────────────────────────────────────────
  console.log('Borrando instancia...');
  await client.deleteInstance(inst.id);
  const { count: afterDelete } = await client.listInstances();
  console.log(`  Instancias restantes: ${afterDelete}`);

  client.disconnect();
  await proc.stop();
}

main().catch((err) => {
  console.error('Error:', err.message);
  process.exit(1);
});
