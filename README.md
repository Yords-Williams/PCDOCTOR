# PC Doctor 1.3

Aplicación de escritorio para diagnóstico, mantenimiento y reparación guiada de equipos Windows.

## Funciones
- Diagnóstico de sistema, memoria, disco y red.
- Detección automática de problemas y recomendaciones.
- Procesos activos mediante `tasklist`.
- Programas de inicio del usuario mediante Registro de Windows.
- Consulta de temperatura mediante WMI cuando el hardware/firmware la expone.
- Análisis local bajo demanda con el motor antivirus propio de PC Doctor.
- Búsqueda de archivos duplicados por contenido SHA-256, con selección y confirmación antes de enviarlos a la Papelera.
- Reparación guiada de DNS, IP y Winsock.
- SFC y DISM.
- Limpieza de disco y herramientas de Windows.
- Historial en SQLite.
- Generación de reporte PDF en la carpeta Descargas del usuario.
- Script para crear instalador `.exe` con `jpackage`.

## Requisitos
Windows 10/11, JDK 17+, Maven 3.9+ y WiX Toolset disponible en `PATH` para generar el instalador `.exe`.

## Ejecutar
```bat
ejecutar.bat
```
o:
```bat
mvn clean javafx:run
```

## Generar instalador
```bat
crear-instalador-windows.bat
```
Esto compila la aplicación, reúne sus dependencias de ejecución y usa `jpackage`. El instalador `.exe` se crea en `dist`. Las versiones creadas con este script comparten un identificador de actualización para reemplazar la instalación anterior. Como el instalador 1.1 no tenía ese identificador fijo, desinstálalo una vez desde Configuración de Windows antes de actualizar. El icono de la ventana se toma de `src/main/resources/icono.png` y el del ejecutable, instalador y accesos directos de `src/main/resources/icono.ico`. Si Windows sigue mostrando el icono anterior tras actualizar, vuelve a crear el acceso directo o reinicia el Explorador de archivos para refrescar su caché de iconos.

El historial de diagnósticos se conserva en `%USERPROFILE%\PCDoctor\pcdoctor.db`; desinstalar o actualizar la aplicación no borra esa base de datos.

## Importante
La temperatura WMI no está disponible en todos los equipos. La aplicación no debe interpretar su ausencia como una falla.

Las operaciones SFC, DISM, Winsock y algunas acciones de red pueden requerir una consola/aplicación ejecutada como administrador. La aplicación pide confirmación antes de las reparaciones con mayor impacto.

El motor antivirus es propio y funciona bajo demanda. Actualmente utiliza hashes SHA-256, la firma de prueba EICAR y heurística básica para archivos PE. Una exploración limpia solo indica que el motor no encontró coincidencias conocidas y no garantiza que el equipo esté libre de malware. PC Doctor no proporciona protección en tiempo real ni pone detecciones en cuarentena automáticamente. No distribuya afirmaciones de detección completas hasta ampliar las pruebas y firmas del motor.

La búsqueda de archivos duplicados se limita a la carpeta elegida por el usuario y conserva un archivo de cada grupo. Los archivos seleccionados solo se envían a la Papelera después de una confirmación explícita. No se eliminan archivos automáticamente ni se modifica el registro de Windows de forma destructiva.
