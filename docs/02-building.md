# Compilar el Engine

El engine es un proyecto Java estándar con Gradle. Una vez compilado obtenés un JAR fat (fat jar / uber jar) que incluye todas las dependencias, así que no necesitás instalar nada extra para correrlo.

---

## Requisitos

- **Java 21 o superior** — el engine usa virtual threads y otras features de Java 21.
- **Gradle** — viene incluido como wrapper en el proyecto, no hace falta instalarlo globalmente.

Para verificar tu versión de Java:

```bash
java --version
# Debería mostrar algo como: openjdk 21.0.x ...
```

---

## Compilar

Desde la carpeta `core/`:

```bash
# Linux / macOS
./gradlew jar

# Windows
gradlew.bat jar
```

Gradle descarga las dependencias automáticamente en la primera ejecución. El JAR generado queda en:

```
core/build/libs/novacore-engine.jar
```

---

## Dependencias del engine

El proyecto usa estas librerías (todas incluidas en el fat jar):

| Librería | Versión | Para qué |
|---|---|---|
| `gson` | 2.10.1 | Serialización/deserialización JSON |
| `Java-WebSocket` | 1.5.4 | Servidor WebSocket para los eventos |
| `slf4j-api` + `slf4j-simple` | 2.0.9 | Logging interno |

---

## Correr el engine manualmente

Una vez compilado, podés correrlo directamente desde la línea de comandos para probar:

```bash
java -jar core/build/libs/novacore-engine.jar \
  --port 7878 \
  --ws-port 7879 \
  --threads 32 \
  --instances-dir /ruta/a/instancias \
  --log-dir /ruta/a/logs \
  --launcher-name MiLauncher \
  --log-level INFO
```

Cuando veas esto en la salida, el engine está listo:

```
[Core] HTTP  → http://localhost:7878
[Core] WS    → ws://localhost:7879
[Core] Ready
```

### Parámetros disponibles

| Parámetro | Default | Descripción |
|---|---|---|
| `--port` | `7878` | Puerto HTTP |
| `--ws-port` | `7879` | Puerto WebSocket |
| `--threads` | `32` | Threads máximos para descargas. `0` = auto (detecta CPU) |
| `--instances-dir` | `./instances` | Directorio raíz de instancias |
| `--log-dir` | `../logs` | Directorio para los archivos de log |
| `--launcher-name` | `novacore-engine` | Nombre del launcher (aparece en logs y branding) |
| `--log-level` | `INFO` | Nivel de log: `DEBUG`, `INFO`, `WARN`, `ERROR` |

---

## Usar el script de build incluido (Windows)

El proyecto también incluye `core/build.bat` para Windows si preferís no usar Gradle directamente. Aunque para la mayoría de los casos el wrapper de Gradle es suficiente.

---

## Reconstruir desde cero

Si querés limpiar el build anterior:

```bash
# Linux / macOS
./gradlew clean jar

# Windows
gradlew.bat clean jar
```

---

## Verificar que funciona

Con el engine corriendo, podés verificar que responde correctamente desde otra terminal:

```bash
curl http://localhost:7878/api
```

Deberías recibir algo como:

```json
{
  "name": "novacore-engine",
  "vendor": "NovaStepStudios",
  "version": "1.0.0",
  "java": "21.0.x",
  "os": "Linux",
  "endpoints": { ... }
}
```
