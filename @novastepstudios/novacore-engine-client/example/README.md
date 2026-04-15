# NovaCore Engine Client - Ejemplos 🚀

Esta carpeta contiene scripts de ejemplo para demostrar cómo integrar NovaCore Engine en tus aplicaciones Node.js o Electron.

## Requisitos previos

1.  **Motor Compilado**: Asegúrate de haber compilado el motor Java. El JAR debe estar en `core/build/libs/novacore-engine.jar` (o ajusta la ruta en los scripts).
2.  **Java 21**: El motor requiere Java 21 o superior instalado en el sistema.
3.  **Dependencias**: Instala las dependencias del cliente (si estás en la raíz del repositorio, ejecuta `npm install`).

## Cómo ejecutar los ejemplos

Desde la raíz del repositorio, utiliza `node` para ejecutar cualquier ejemplo:

```bash
# Instalación de Vanilla 1.21.4
node @novastepstudios/novacore-engine-client/example/install-vanilla.js

# Lanzamiento de Vanilla 1.21.4 (después de instalar)
node @novastepstudios/novacore-engine-client/example/launch-vanilla.js

# Instalación de Fabric
node @novastepstudios/novacore-engine-client/example/install-fabric.js

# Ciclo de vida completo (Recomendado para entender el flujo)
node @novastepstudios/novacore-engine-client/example/full-lifecycle.js
```

## Contenido de los ejemplos

- 📂 `install-*.js`: Demuestra cómo usar `client.install()` con callbacks de progreso detallados y barra de progreso en consola.
- 📂 `launch-*.js`: Muestra cómo iniciar el juego, capturar logs en tiempo real y manejar el cierre/crash del proceso.
- 📂 `full-lifecycle.js`: El ejemplo más completo. Inicia el proceso del motor, realiza una instalación, lanza el juego y apaga el motor limpiamente al finalizar.

---

> [!TIP]
> Todos los ejemplos utilizan rutas relativas a esta carpeta para crear el directorio `.minecraft`. Puedes borrarlos en cualquier momento para limpiar el espacio.
