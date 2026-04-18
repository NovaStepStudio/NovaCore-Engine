# NovaCore-Engine: Ejemplos Completos (JavaScript)

Estos ejemplos estan creados para ejecutarse usando Node nativamente asegurandose de abarcar el potencial exacto de NovaCore-Engine. Cada script se enfoca en un area especifica. Importan las declaraciones JSDoc para que tu editor proporcione auto-completado nativo.

Te recomendamos analizarlos uno a uno para presenciar la integracion.

---

### 1. 01-instances-crud.js
El manual del Manejador de Instancias. Este script te muestra como crear un perfil (seteando la version, RAM y el tipo de Recolector de Basura / GC), luego lista todas las instancias guardadas, le actualiza la RAM mediante PATCH y finalmente purga el registro usando la API en caliente.

### 2. 02-shared-cache-install.js
Muestra la mayor ventaja de NovaCore. Instala Minecraft en una instancia "A", luego inmediatamente lo instala en una instancia "B" apuntando al mismo sharedPath. Veras como en la consola la segunda vez el indicador SkippedFiles domina, evitando re-descargar los assets/libs/java.

### 3. 03-modloader-orchestration.js
NovaCore gestiona los mods. Revisa este ejemplo para comunicarte con el ModLoaderOrchestrator. Pide las versiones remotas compatibles con Forge/Fabric en un segundo plano e inyecta asincronamente el cliente y las librerias a la instancia elegida. 

### 4. 04-advanced-launch.js
Demuestra como exprimir el maximo rendimiento. Evalua los procesadores instalados y tu Memoria RAM viva ejecutando el API (system/resources). Luego le pide a NovaCore arrancar la instancia usando esos parametros y variables especiales de entorno.

### 5. 05-full-telemetry-ws.js
Intervencion de sistema. Enchufa al WebSocket conectando un listener puro (onAny), mostrandote toda decision del backend en tiempo real (Descargas interceptadas, recuperacion de descargas pausadas con recovery_state, y logs).

### 6. 06-graceful-tree-kill.js
El ejemplo del cierre definitivo. Usando closeEngine, limpia el arbol de procesos para asegurar la liberacion total de RAM del sistema asincronamente.

---
### Para ejecutar
Estos ejemplos son validos usando unicamente el ecosistema basico de node:
```bash
node 02-shared-cache-install.js
```
