# Ejemplos de NovaCore-Engine-Client

Esta carpeta contiene una suite de 15 ejemplos que demuestran todas las capacidades del motor NovaCore.

## Configuración Previa
Todos los ejemplos usan el archivo `00-config.js` para localizar el JAR del motor y las carpetas de trabajo. Asegúrate de haber compilado el motor Java antes de ejecutarlos.

## Lista de Ejemplos

| # | Archivo | Descripción |
|---|---|---|
| 01 | `01-install-and-launch-using-instance-config.js` | Install con `isInstance` + launch mínimo (usa config persistida). |
| 02 | `02-vanilla-install.js` | Instalación completa de Minecraft Vanilla. |
| 03 | `03-modloader-install.js` | Instalación de Forge y Fabric. |
| 04 | `04-basic-launch.js` | Lanzamiento del juego y captura de logs. |
| 05 | `05-event-monitor.js` | Telemetría en tiempo real vía WebSockets. |
| 06 | `06-process-management.js` | Listar y cerrar instancias en ejecución. |
| 07 | `07-engine-info.js` | Información de hardware y recomendaciones. |
| 08 | `08-runtime-manager.js` | Descarga de Java Runtimes específicos. |
| 09 | `09-jvm-customization.js` | Ajustes de memoria y Garbage Collector. |
| 10 | `10-telemetry-crashes.js` | Reportes de errores y historial de sesiones. |
| 11 | `11-recovery-system.js` | Recuperación de descargas interrumpidas. |
| 12 | `12-auth-modes.js` | Modos Offline vs Microsoft Auth. |
| 13 | `13-world-saves.js` | Explorador de mundos (saves) de Minecraft. |
| 14 | `14-mod-checker.js` | Integridad de ModLoaders y limpieza de estado. |
| 15 | `15-comprehensive-flow.js` | Ciclo completo: De cero a jugar. |

## Cómo ejecutarlos
Desde la raíz del cliente, asegúrate de haber construido el proyecto (`npm run build`) y luego ejecuta:

```bash
node example/01-install-and-launch-using-instance-config.js
```

> [!WARNING]
> Algunos de estos archivos pueden estar desactualizados o incompletos. Durante las fases de prueba, se generaron y compilaron como parte del desarrollo del motor.
> Por este motivo, es posible que contengan características, datos o implementaciones que fueron consideradas en versiones anteriores de NovaCore-Engine, pero que posteriormente se modificaron o descartaron.
