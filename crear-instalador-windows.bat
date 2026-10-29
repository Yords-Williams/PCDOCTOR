@echo off
setlocal
echo === PC Doctor - instalador Windows ===
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

set "CLAMAV_SOURCE=vendor-download\clamav-unpack\clamav-1.5.4.win.x64"
if not exist "%CLAMAV_SOURCE%\clamscan.exe" if not exist "%CLAMAV_SOURCE%\freshclam.exe" (
  echo ERROR: faltan los ejecutables de ClamAV en %CLAMAV_SOURCE%
  pause
  exit /b 1
)
if exist target\jpackage-input\clamav rmdir /S /Q target\jpackage-input\clamav
mkdir target\jpackage-input\clamav
copy /Y "%CLAMAV_SOURCE%\clamscan.exe" target\jpackage-input\clamav\ >nul
copy /Y "%CLAMAV_SOURCE%\freshclam.exe" target\jpackage-input\clamav\ >nul
copy /Y "%CLAMAV_SOURCE%\libclamav.dll" target\jpackage-input\clamav\ >nul
copy /Y "%CLAMAV_SOURCE%\libfreshclam.dll" target\jpackage-input\clamav\ >nul
copy /Y "%CLAMAV_SOURCE%\*.dll" target\jpackage-input\clamav\ >nul
copy /Y "%CLAMAV_SOURCE%\COPYING.txt" target\jpackage-input\clamav\ >nul
xcopy /E /I /Q /Y "%CLAMAV_SOURCE%\certs" target\jpackage-input\clamav\certs >nul
if exist target\clamav-database (
  mkdir target\jpackage-input\clamav\database
  copy /Y target\clamav-database\*.cvd target\jpackage-input\clamav\database\ >nul
  copy /Y target\clamav-database\*.sign target\jpackage-input\clamav\database\ >nul
)
echo bundled> target\jpackage-input\clamav\.pcdoctor-bundled
if errorlevel 1 (
  echo ERROR al preparar el archivo JAR de la aplicacion.
  pause
  exit /b 1
)

if not exist dist mkdir dist
if exist "dist\PC Doctor-1.3.0.exe" del /Q "dist\PC Doctor-1.3.0.exe"
jpackage --type exe --name "PC Doctor" --app-version 1.3.0 --vendor "PC Doctor" --win-upgrade-uuid 3F1391D7-8A59-4E5D-BD8A-6D3D6C4A2F11 --input target\jpackage-input --main-jar pc-doctor-1.3.0.jar --main-class com.pcdoctor.Launcher --icon src\main\resources\icono.ico --dest dist --win-shortcut --win-menu --win-dir-chooser
if errorlevel 1 (
  echo ERROR al generar el instalador. Verifica que WiX Toolset este instalado y disponible en PATH.
  pause
  exit /b 1
)
echo.
echo Instalador generado en dist\PC Doctor-1.3.0.exe
pause
