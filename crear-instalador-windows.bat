@echo off
setlocal
echo === PC Doctor Soporte - instalador Windows ===
set APP_VERSION=1.3.6
where mvn >nul 2>nul
if errorlevel 1 (
  echo ERROR: Maven no esta instalado o no esta en PATH.
  pause
  exit /b 1
)
where jpackage >nul 2>nul
if errorlevel 1 (
  echo ERROR: jpackage no esta disponible. Instala JDK 17 o superior.
  pause
  exit /b 1
)
call mvn clean package -DskipTests
if errorlevel 1 (
  echo ERROR al compilar.
  pause
  exit /b 1
)

if not exist target\jpackage-input mkdir target\jpackage-input
if exist target\jpackage-input\*.jar del /Q target\jpackage-input\*.jar
call mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\jpackage-input
if errorlevel 1 (
  echo ERROR al copiar las dependencias de ejecucion.
  pause
  exit /b 1
)
copy /Y target\pc-doctor-1.3.0.jar target\jpackage-input\
if errorlevel 1 (
  echo ERROR al preparar el archivo JAR de la aplicacion.
  pause
  exit /b 1
)

del /Q target\jpackage-input\javafx-base-21.0.4.jar 2>nul
del /Q target\jpackage-input\javafx-controls-21.0.4.jar 2>nul
del /Q target\jpackage-input\javafx-fxml-21.0.4.jar 2>nul
del /Q target\jpackage-input\javafx-graphics-21.0.4.jar 2>nul

if not exist dist mkdir dist
if exist "dist\PC Doctor Soporte-1.3.6.exe" del /Q "dist\PC Doctor Soporte-1.3.6.exe"
if exist target\app-image rmdir /S /Q target\app-image

REM Construir primero la imagen de aplicacion que ya contiene un runtime probado.
jpackage --type app-image --name "PC Doctor Soporte" --app-version %APP_VERSION% --input target\jpackage-input --main-jar pc-doctor-1.3.0.jar --main-class com.pcdoctor.Launcher --icon src\main\resources\icono.ico --dest target\app-image --java-options "-Dfile.encoding=UTF-8" --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"
if errorlevel 1 (
  echo ERROR al construir la imagen de la aplicacion.
  pause
  exit /b 1
)

REM El instalador se crea desde esa imagen: evita que se genere un runtime distinto.
jpackage --type exe --name "PC Doctor Soporte" --app-version %APP_VERSION% --vendor "PC Doctor" --win-upgrade-uuid 3F1391D7-8A59-4E5D-BD8A-6D3D6C4A2F11 --app-image "target\app-image\PC Doctor Soporte" --icon src\main\resources\icono.ico --dest dist --win-shortcut --win-menu --win-dir-chooser --win-per-user-install
if errorlevel 1 (
  echo ERROR al generar el instalador. Verifica que WiX Toolset este instalado y disponible en PATH.
  pause
  exit /b 1
)
echo.
echo Instalador generado en dist\PC Doctor Soporte-1.3.6.exe
pause
