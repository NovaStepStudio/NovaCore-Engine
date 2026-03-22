'use strict';

const path = require('path');

/**
 * Configuración compartida para los ejemplos.
 * Editá estas rutas antes de correr los ejemplos.
 */
module.exports = {
  // Ruta al JAR compilado del engine
  jarPath: path.resolve(__dirname, '../../core/build/libs/novacore-engine.jar'),

  // Directorio donde se guardarán las instancias
  instancesDir: path.resolve(__dirname, '../../../instances'),

  // Directorio de logs del engine
  logDir: path.resolve(__dirname, '../../../logs'),

  // Shared path para compartir assets/libs entre instancias
  sharedPath: path.resolve(__dirname, '../../../shared'),

  // Versión de Minecraft para los ejemplos
  mcVersion: '1.21.1',

  // Nombre del launcher (para branding)
  launcherName: 'StepLauncher',
};
