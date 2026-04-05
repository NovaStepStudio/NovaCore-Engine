@echo off
setlocal EnableDelayedExpansion

set "ROOT=%CD%"
set VERSION=1.0.0
set MAIN_CLASS=dev.novastep.core.Main
set "SRC_DIR=%ROOT%\src\main\java"
set "BUILD_CLASSES=%ROOT%\build\classes"
set "LIB_DIR=%ROOT%\build\libs\deps"
set "EXTRACT_DIR=%ROOT%\build\extract"
set "JAR_OUT=%ROOT%\build\libs\novacore-engine.jar"
set "MANIFEST=%ROOT%\build\MANIFEST.MF"
set "SOURCES_FILE=%ROOT%\build\sources.txt"

echo [Build] novacore-engine v%VERSION% - NovaStepStudios
echo.

java -version >NUL 2>&1
if %ERRORLEVEL% neq 0 ( echo [ERROR] Java no encontrado. & exit /b 1 )
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo [Info] Java: %%v
echo.

if not exist "%BUILD_CLASSES%" mkdir "%BUILD_CLASSES%"
if not exist "%LIB_DIR%"       mkdir "%LIB_DIR%"
if not exist "%ROOT%\build\libs" mkdir "%ROOT%\build\libs"

echo [Deps] Verificando dependencias...
set "GSON=%LIB_DIR%\gson-2.10.1.jar"
set "WS=%LIB_DIR%\java-websocket-1.5.4.jar"
set "SLF4J_API=%LIB_DIR%\slf4j-api-2.0.9.jar"
set "SLF4J_SIMPLE=%LIB_DIR%\slf4j-simple-2.0.9.jar"
set "CFR=%LIB_DIR%\cfr-0.152.jar"

if not exist "%GSON%"         powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar' -OutFile '%GSON%' -UseBasicParsing"
if not exist "%WS%"           powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.4/Java-WebSocket-1.5.4.jar' -OutFile '%WS%' -UseBasicParsing"
if not exist "%SLF4J_API%"    powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar' -OutFile '%SLF4J_API%' -UseBasicParsing"
if not exist "%SLF4J_SIMPLE%" powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar' -OutFile '%SLF4J_SIMPLE%' -UseBasicParsing"
if not exist "%CFR%" powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar' -OutFile '%CFR%' -UseBasicParsing"

for %%f in ("%GSON%" "%WS%" "%SLF4J_API%" "%SLF4J_SIMPLE%" "%CFR%") do (
    if not exist %%f ( echo [ERROR] Falta dep: %%f & exit /b 1 )
)
echo [Deps] OK.
echo.

echo [Compile] Recolectando fuentes...
if exist "%SOURCES_FILE%" del "%SOURCES_FILE%"
for /r "%SRC_DIR%" %%f in (*.java) do echo %%f>> "%SOURCES_FILE%"
for /f %%i in ('type "%SOURCES_FILE%" ^| find /c /v ""') do echo [Compile] %%i archivos .java encontrados.

set "CP=%GSON%;%WS%;%SLF4J_API%;%SLF4J_SIMPLE%;%CFR%"
javac -encoding UTF-8 -cp "%CP%" -d "%BUILD_CLASSES%" @"%SOURCES_FILE%"
if %ERRORLEVEL% neq 0 ( echo [ERROR] Compilacion fallida. & exit /b 1 )
echo [Compile] OK.
echo.

echo [Package] Creando fat JAR...
if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
mkdir "%EXTRACT_DIR%"

cd /d "%EXTRACT_DIR%"
jar xf "%GSON%"
jar xf "%WS%"
jar xf "%SLF4J_API%"
jar xf "%SLF4J_SIMPLE%"
jar xf "%CFR%"
cd /d "%ROOT%"

xcopy /s /y /q "%BUILD_CLASSES%\*" "%EXTRACT_DIR%\" >NUL

(
echo Main-Class: %MAIN_CLASS%
echo Implementation-Title: novacore-engine
echo Implementation-Vendor: NovaStepStudios
echo Implementation-Version: %VERSION%
echo.
) > "%MANIFEST%"

cd /d "%EXTRACT_DIR%"
jar cfm "%JAR_OUT%" "%MANIFEST%" .
cd /d "%ROOT%"

rmdir /s /q "%EXTRACT_DIR%"
del "%SOURCES_FILE%"

if not exist "%JAR_OUT%" ( echo [ERROR] No se creo el JAR. & exit /b 1 )
echo [Package] OK.
echo.
echo ====================================================
echo  Build exitoso!
echo  JAR: build\libs\novacore-engine.jar
echo ====================================================
echo.
echo Para ejecutar:
echo    java -jar build\libs\novacore-engine.jar
echo    java -jar build\libs\novacore-engine.jar --port 7878 --ws-port 7879 --threads 32
