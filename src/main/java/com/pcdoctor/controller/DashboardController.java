package com.pcdoctor.controller;

import com.pcdoctor.model.Diagnostico;
import com.pcdoctor.model.Problema;
import com.pcdoctor.service.AntivirusService;
import com.pcdoctor.service.ArchivoDuplicadoService;
import com.pcdoctor.service.DiagnosticoService;
import com.pcdoctor.service.ReparacionService;
import com.pcdoctor.service.ReportePdfService;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class DashboardController {
    @FXML private VBox pageRoot;
    @FXML private Label estadoLabel;

    private final DiagnosticoService diagnosticoService;
    private final ReparacionService reparacionService;
    private final ReportePdfService reportePdfService;
    private final AntivirusService antivirusService;
    private final ArchivoDuplicadoService archivoDuplicadoService;
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "pcdoctor-background");
        thread.setDaemon(true);
        return thread;
    });

    public DashboardController(DiagnosticoService diagnosticoService,
                               ReparacionService reparacionService,
                               ReportePdfService reportePdfService,
                               AntivirusService antivirusService,
                               ArchivoDuplicadoService archivoDuplicadoService) {
        this.diagnosticoService = diagnosticoService;
        this.reparacionService = reparacionService;
        this.reportePdfService = reportePdfService;
        this.antivirusService = antivirusService;
        this.archivoDuplicadoService = archivoDuplicadoService;
    }

    @FXML public void initialize() {
        mostrarPaginaInicio();
    }

    @FXML private void mostrarPaginaInicio() {
        VBox page = crearPagina("Bienvenido a PC Doctor",
                "Selecciona una herramienta en el menú para abrir su pantalla.");
        Label ayuda = new Label("Cada opción muestra aquí sus propios resultados y controles.");
        ayuda.getStyleClass().add("page-card");
        page.getChildren().add(ayuda);
        estadoLabel.setText("Listo");
    }

    @FXML private void ejecutarDiagnostico() {
        VBox page = crearPagina("Diagnóstico completo",
                "Revisa el sistema, memoria, almacenamiento, red y temperatura.");
        Label estado = agregarEstado(page, "Analizando el equipo...");
        TableView<Diagnostico> tabla = crearTablaDiagnosticos(false);
        ListView<String> problemas = new ListView<>();
        problemas.setPlaceholder(new Label("No hay recomendaciones para mostrar."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().addAll(new Label("Resultados del análisis"), tabla,
                new Label("Problemas y recomendaciones"), problemas);
        ejecutarEnSegundoPlano("No se pudo completar el diagnóstico:",
                diagnosticoService::analizarYGuardar,
                resultado -> {
                    tabla.setItems(FXCollections.observableArrayList(resultado.diagnosticos()));
                    problemas.setItems(FXCollections.observableArrayList(
                            resultado.problemas().stream()
                                    .map(this::formatearProblema)
                                    .toList()));
                    long revisar = resultado.diagnosticos().stream()
                            .filter(d -> "REVISAR".equals(d.getResultado())).count();
                    estado.setText(resultado.diagnosticos().size() + " áreas revisadas; "
                            + revisar + " requieren atención.");
                    estadoLabel.setText(revisar == 0 ? "Diagnóstico terminado"
                            : "Diagnóstico terminado con alertas");
                },
                error -> estado.setText(error));
    }

    @FXML private void detectarProblemas() {
        VBox page = crearPagina("Problemas detectados",
                "Alertas y recomendaciones generadas a partir del estado actual del equipo.");
        Label estado = agregarEstado(page, "Buscando problemas...");
        ListView<String> lista = new ListView<>();
        lista.setPlaceholder(new Label("No hay recomendaciones para mostrar."));
        VBox.setVgrow(lista, Priority.ALWAYS);
        page.getChildren().add(lista);
        ejecutarEnSegundoPlano("No se pudieron detectar los problemas:",
                diagnosticoService::detectarProblemas,
                problemas -> {
                    lista.setItems(FXCollections.observableArrayList(
                            problemas.stream().map(this::formatearProblema).toList()));
                    estado.setText(problemas.size() + " recomendaciones.");
                    estadoLabel.setText("Detección terminada");
                },
                error -> estado.setText(error));
    }

    @FXML private void mostrarProcesos() {
        mostrarTablaDatos("Procesos activos", "Procesos que se están ejecutando en Windows.",
                "Consultando procesos...",
                List.of("Imagen", "PID", "Sesión", "N.º sesión", "Memoria"),
                diagnosticoService::obtenerProcesos);
    }

    @FXML private void mostrarInicio() {
        mostrarTablaDatos("Programas de inicio",
                "Programas configurados para iniciarse automáticamente con Windows.",
                "Consultando programas de inicio...",
                List.of("Nombre", "Comando", "Ubicación", "Usuario"),
                diagnosticoService::obtenerInicioWindows);
    }

    @FXML private void mostrarTemperatura() {
        VBox page = crearPagina("Temperatura del equipo",
                "Lecturas térmicas publicadas por el firmware mediante WMI.");
        Label estado = agregarEstado(page, "Consultando sensores térmicos...");
        Label lectura = new Label("Esperando lectura...");
        lectura.getStyleClass().add("result-value");
        Label detalle = new Label("La disponibilidad depende del firmware del equipo.");
        detalle.getStyleClass().add("result-description");
        VBox tarjeta = new VBox(10, new Label("LECTURA TÉRMICA"), lectura, detalle);
        tarjeta.getStyleClass().add("result-card");
        page.getChildren().add(tarjeta);
        ejecutarEnSegundoPlano("No se pudo consultar la temperatura:",
                diagnosticoService::obtenerTemperatura,
                resultado -> {
                    boolean noDisponible = resultado.startsWith("No disponible");
                    lectura.setText(noDisponible ? "No disponible" : resultado);
                    detalle.setText(noDisponible
                            ? resultado
                            : "Lectura proporcionada por los sensores WMI de Windows.");
                    estado.setText(noDisponible
                            ? "Este equipo no publicó sensores térmicos WMI."
                            : "Lectura térmica obtenida.");
                    estadoLabel.setText(estado.getText());
                },
                error -> {
                    lectura.setText("No se pudo obtener la lectura");
                    detalle.setText(error);
                    estado.setText("Error al consultar la temperatura");
                    estadoLabel.setText("Error");
                });
    }

    @FXML private void mostrarAntivirus() {
        VBox page = crearPagina("Análisis antivirus",
                "Análisis bajo demanda con el motor ClamAV instalado en este equipo.");
        Label estado = agregarEstado(page, "Buscando instalación de ClamAV...");
        Label detalle = new Label("ClamAV no ofrece protección en tiempo real desde PC Doctor. "
                + "Las detecciones se muestran para revisión; no se borran ni se ponen en cuarentena.");
        detalle.setWrapText(true);
        detalle.getStyleClass().add("result-description");
        Label carpeta = new Label("Ninguna carpeta seleccionada.");
        carpeta.setWrapText(true);
        Button actualizar = new Button("Actualizar firmas");
        Button analizar = new Button("Elegir carpeta y analizar");
        actualizar.setDisable(true);
        analizar.setDisable(true);
        TableView<AntivirusService.Detection> detecciones = crearTablaAntivirus();
        TextArea incidencias = new TextArea();
        incidencias.setEditable(false);
        incidencias.setWrapText(true);
        incidencias.setPrefRowCount(4);
        incidencias.setPromptText("Los detalles del motor aparecerán aquí.");
        VBox.setVgrow(detecciones, Priority.ALWAYS);
        page.getChildren().addAll(detalle, actualizar, carpeta, analizar,
                new Label("Detecciones"), detecciones,
                new Label("Detalles del análisis o actualización"), incidencias);

        Optional<AntivirusService.Installation> installation = antivirusService.findInstallation();
        if (installation.isEmpty()) {
            estado.setText("ClamAV no está instalado. Instálalo y configura CLAMAV_HOME "
                    + "o añade la carpeta de ClamAV al PATH; después reinicia PC Doctor.");
            incidencias.setText("PC Doctor busca clamscan.exe y freshclam.exe en CLAMAV_HOME, "
                    + "C:\\Program Files\\ClamAV, C:\\Program Files (x86)\\ClamAV y PATH.");
        } else {
            AntivirusService.Installation clamAv = installation.orElseThrow();
            estado.setText("ClamAV encontrado en " + clamAv.directory());
            actualizar.setDisable(false);
            analizar.setDisable(false);
        }

        actualizar.setOnAction(event -> {
            actualizar.setDisable(true);
            analizar.setDisable(true);
            incidencias.clear();
            estado.setText("Actualizando la base de firmas con freshclam...");
            estadoLabel.setText(estado.getText());
            ejecutarEnSegundoPlano("No se pudieron actualizar las firmas:",
                    antivirusService::updateSignatures,
                    resultado -> {
                        incidencias.setText(resultado.hasOutput() ? resultado.output()
                                : "freshclam terminó sin informar cambios ni errores.");
                        estado.setText("Actualización de firmas finalizada.");
                        estadoLabel.setText("Firmas ClamAV actualizadas");
                        actualizar.setDisable(false);
                        analizar.setDisable(false);
                    },
                    error -> {
                        incidencias.setText(error);
                        estado.setText("Falló la actualización de firmas.");
                        estadoLabel.setText("Error al actualizar ClamAV");
                        actualizar.setDisable(false);
                        analizar.setDisable(false);
                    });
        });

        analizar.setOnAction(event -> {
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle("Selecciona una carpeta para analizar");
            File seleccion = selector.showDialog(pageRoot.getScene().getWindow());
            if (seleccion == null) {
                return;
            }
            Path directorio = seleccion.toPath();
            carpeta.setText("Carpeta: " + directorio.toAbsolutePath());
            detecciones.getItems().clear();
            incidencias.clear();
            actualizar.setDisable(true);
            analizar.setDisable(true);
            estado.setText("Analizando archivos con ClamAV...");
            estadoLabel.setText(estado.getText());
            ejecutarEnSegundoPlano("No se pudo completar el análisis antivirus:",
                    () -> antivirusService.scan(directorio),
                    resultado -> {
                        detecciones.setItems(FXCollections.observableArrayList(
                                resultado.detections()));
                        incidencias.setText(resultado.output());
                        if (resultado.completedWithErrors()) {
                            estado.setText("ClamAV terminó con errores; revisa los detalles. "
                                    + resultado.detections().size() + " detección(es) reportada(s).");
                            estadoLabel.setText("Análisis con errores");
                        } else if (resultado.exitCode() == 1
                                && resultado.detections().isEmpty()) {
                            estado.setText("ClamAV informó una detección, pero no se pudo "
                                    + "interpretar la salida. Revisa los detalles.");
                            estadoLabel.setText("Revisar salida de ClamAV");
                        } else if (resultado.detections().isEmpty()) {
                            estado.setText("ClamAV no encontró detecciones en "
                                    + resultado.filesScanned() + " archivos.");
                            estadoLabel.setText("Análisis finalizado");
                        } else {
                            estado.setText(resultado.detections().size() + " detección(es), "
                                    + resultado.filesScanned() + " archivo(s) analizado(s).");
                            estadoLabel.setText("Se encontraron detecciones");
                        }
                        actualizar.setDisable(false);
                        analizar.setDisable(false);
                    },
                    error -> {
                        estado.setText(error);
                        estadoLabel.setText("Error en análisis antivirus");
                        actualizar.setDisable(false);
                        analizar.setDisable(false);
                    });
        });
    }

    private TableView<AntivirusService.Detection> crearTablaAntivirus() {
        TableView<AntivirusService.Detection> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Todavía no hay detecciones."));
        TableColumn<AntivirusService.Detection, String> nombre = new TableColumn<>("Firma");
        nombre.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().name()));
        TableColumn<AntivirusService.Detection, String> archivo = new TableColumn<>("Archivo");
        archivo.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().file().toString()));
        tabla.getColumns().add(nombre);
        tabla.getColumns().add(archivo);
        return tabla;
    }

    @FXML private void buscarDuplicados() {
        VBox page = crearPagina("Archivos duplicados",
                "Elige una carpeta. Los archivos se comparan por tamaño y SHA-256; se conserva "
                        + "uno de cada grupo y puedes desmarcar los que quieras conservar.");
        Label estado = agregarEstado(page, "Selecciona una carpeta para empezar.");
        Label carpetaLabel = new Label("Ninguna carpeta seleccionada.");
        carpetaLabel.setWrapText(true);
        Button buscar = new Button("Elegir carpeta y buscar");
        Button enviarPapelera = new Button("Enviar seleccionados a la Papelera");
        enviarPapelera.setDisable(true);
        TableView<CandidatoDuplicado> tabla = crearTablaDuplicados();
        TextArea errores = new TextArea();
        errores.setEditable(false);
        errores.setWrapText(true);
        errores.setPrefRowCount(4);
        errores.setPromptText("Los errores al mover archivos aparecerán aquí.");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().addAll(carpetaLabel, buscar, tabla, enviarPapelera, errores);

        buscar.setOnAction(event -> {
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle("Selecciona una carpeta para buscar duplicados");
            File seleccion = selector.showDialog(pageRoot.getScene().getWindow());
            if (seleccion == null) {
                return;
            }
            Path directorio = seleccion.toPath();
            carpetaLabel.setText("Carpeta: " + directorio.toAbsolutePath());
            tabla.getItems().clear();
            enviarPapelera.setDisable(true);
            buscar.setDisable(true);
            errores.clear();
            estado.setText("Buscando archivos duplicados...");
            estadoLabel.setText(estado.getText());
            ejecutarEnSegundoPlano("No se pudo analizar la carpeta:",
                    () -> archivoDuplicadoService.buscar(directorio),
                    grupos -> {
                        List<CandidatoDuplicado> candidatos = grupos.stream()
                                .flatMap(grupo -> grupo.duplicados().stream()
                                        .map(duplicado -> new CandidatoDuplicado(
                                                duplicado, grupo.original())))
                                .toList();
                        tabla.setItems(FXCollections.observableArrayList(candidatos));
                        estado.setText(candidatos.isEmpty()
                                ? "No se encontraron archivos duplicados."
                                : candidatos.size() + " duplicados encontrados. "
                                + "Desmarca lo que no quieras mover a la Papelera.");
                        estadoLabel.setText("Búsqueda de duplicados finalizada");
                        enviarPapelera.setDisable(candidatos.isEmpty() || !puedeEnviarPapelera());
                        buscar.setDisable(false);
                        if (!candidatos.isEmpty() && !puedeEnviarPapelera()) {
                            errores.setText("Esta versión de Java no permite enviar archivos "
                                    + "a la Papelera desde la aplicación.");
                        }
                    },
                    error -> {
                        estado.setText(error);
                        estadoLabel.setText("Error");
                        buscar.setDisable(false);
                    });
        });

        enviarPapelera.setOnAction(event -> {
            List<CandidatoDuplicado> seleccionados = tabla.getItems().stream()
                    .filter(CandidatoDuplicado::seleccionado)
                    .toList();
            if (seleccionados.isEmpty()) {
                estado.setText("Marca al menos un duplicado para continuar.");
                return;
            }
            if (!confirmar("Enviar duplicados a la Papelera",
                    "Se enviarán " + seleccionados.size()
                            + " archivos seleccionados a la Papelera. El archivo original "
                            + "de cada grupo se conservará. ¿Continuar?")) {
                return;
            }

            enviarPapelera.setDisable(true);
            buscar.setDisable(true);
            estado.setText("Enviando archivos a la Papelera...");
            estadoLabel.setText(estado.getText());
            ejecutarEnSegundoPlano("No se pudieron mover los archivos:",
                    () -> moverAPapelera(seleccionados),
                    resumen -> {
                        List<Path> movidos = resumen.movidos();
                        tabla.getItems().removeIf(candidato -> movidos.contains(candidato.archivo()));
                        errores.setText(String.join(System.lineSeparator(), resumen.errores()));
                        estado.setText(movidos.size() + " archivo(s) enviado(s) a la Papelera; "
                                + resumen.errores().size() + " error(es).");
                        estadoLabel.setText(resumen.errores().isEmpty()
                                ? "Limpieza finalizada" : "Limpieza finalizada con errores");
                        buscar.setDisable(false);
                        enviarPapelera.setDisable(tabla.getItems().isEmpty()
                                || !puedeEnviarPapelera());
                    },
                    error -> {
                        errores.setText(error);
                        estado.setText("No se pudieron mover los archivos.");
                        estadoLabel.setText("Error");
                        buscar.setDisable(false);
                        enviarPapelera.setDisable(false);
                    });
        });
    }

    private TableView<CandidatoDuplicado> crearTablaDuplicados() {
        TableView<CandidatoDuplicado> tabla = new TableView<>();
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Selecciona una carpeta para comenzar."));

        TableColumn<CandidatoDuplicado, Boolean> seleccionar = new TableColumn<>("Enviar");
        seleccionar.setCellValueFactory(c -> c.getValue().seleccionadoProperty());
        seleccionar.setCellFactory(CheckBoxTableCell.forTableColumn(seleccionar));
        seleccionar.setEditable(true);
        seleccionar.setMaxWidth(90);

        TableColumn<CandidatoDuplicado, String> archivo = new TableColumn<>("Archivo duplicado");
        archivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().archivo().toString()));
        TableColumn<CandidatoDuplicado, String> conservar = new TableColumn<>("Se conservará");
        conservar.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().original().toString()));
        tabla.getColumns().addAll(seleccionar, archivo, conservar);
        return tabla;
    }

    private TrashSummary moverAPapelera(List<CandidatoDuplicado> candidatos) {
        if (!puedeEnviarPapelera()) {
            throw new IllegalStateException("No está disponible la función de Papelera de reciclaje.");
        }
        List<Path> movidos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        for (CandidatoDuplicado candidato : candidatos) {
            try {
                if (Desktop.getDesktop().moveToTrash(candidato.archivo().toFile())) {
                    movidos.add(candidato.archivo());
                } else {
                    errores.add(candidato.archivo() + ": Windows no pudo moverlo a la Papelera.");
                }
            } catch (SecurityException | UnsupportedOperationException e) {
                errores.add(candidato.archivo() + ": " + e.getMessage());
            }
        }
        return new TrashSummary(movidos, errores);
    }

    private boolean puedeEnviarPapelera() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    @FXML private void generarReporte() {
        VBox page = crearPagina("Reporte de diagnóstico",
                "Genera un informe PDF con los resultados guardados en este equipo.");
        Label estado = agregarEstado(page, "Generando el reporte...");
        Label ruta = new Label("Preparando el informe...");
        ruta.setWrapText(true);
        ruta.getStyleClass().add("result-description");
        Button abrir = new Button("Abrir reporte PDF");
        abrir.setDisable(true);
        VBox tarjeta = new VBox(12, new Label("INFORME PDF"), ruta, abrir);
        tarjeta.getStyleClass().add("result-card");
        page.getChildren().add(tarjeta);
        ejecutarEnSegundoPlano("No se pudo generar el PDF:",
                reportePdfService::generar,
                reporte -> {
                    ruta.setText("Guardado en: " + reporte.toAbsolutePath());
                    abrir.setDisable(!puedeAbrirArchivos());
                    abrir.setOnAction(event -> abrirReporte(reporte, estado));
                    estado.setText(puedeAbrirArchivos()
                            ? "Reporte generado y listo para abrir."
                            : "Reporte generado; ábrelo desde la ruta indicada.");
                    estadoLabel.setText("Reporte PDF listo");
                },
                error -> {
                    ruta.setText(error);
                    estado.setText("No se pudo generar el reporte.");
                    estadoLabel.setText("Error");
                });
    }

    @FXML private void limpiarDns() {
        ejecutarAccion("Limpiar DNS", "Vacía la caché de resolución de nombres de Windows.",
                "Limpiando la caché DNS...", reparacionService::limpiarDns);
    }

    @FXML private void renovarIp() {
        if (confirmar("Renovar IP", "Puede desconectar temporalmente la red. ¿Continuar?")) {
            ejecutarAccion("Renovar dirección IP", "Libera y solicita una nueva dirección IP.",
                    "Renovando la dirección IP...", reparacionService::renovarIp);
        }
    }

    @FXML private void resetWinsock() {
        if (confirmar("Restablecer Winsock", "Puede requerir reiniciar Windows. ¿Continuar?")) {
            ejecutarAccion("Restablecer Winsock", "Restablece el catálogo de red de Windows.",
                    "Restableciendo Winsock...", reparacionService::resetWinsock);
        }
    }

    @FXML private void sfc() {
        if (confirmar("SFC /scannow", "Puede tardar varios minutos. ¿Ejecutarlo?")) {
            ejecutarAccion("Comprobación de archivos de Windows",
                    "Busca y repara archivos protegidos del sistema.",
                    "Ejecutando SFC /scannow...", reparacionService::comprobarArchivosWindows);
        }
    }

    @FXML private void dism() {
        if (confirmar("DISM RestoreHealth",
                "Puede tardar bastante y normalmente requiere administrador. ¿Continuar?")) {
            ejecutarAccion("Reparar imagen de Windows",
                    "Usa DISM para comprobar y reparar la imagen de Windows.",
                    "Ejecutando DISM RestoreHealth...", reparacionService::repararImagenWindows);
        }
    }

    @FXML private void limpiezaDisco() {
        ejecutarAccion("Limpieza de disco", "Abre la herramienta de limpieza de Windows.",
                "Abriendo Limpieza de disco...", reparacionService::abrirLimpiezaDisco);
    }

    @FXML private void administradorTareas() {
        ejecutarAccion("Administrador de tareas", "Abre el Administrador de tareas de Windows.",
                "Abriendo el Administrador de tareas...", reparacionService::abrirAdministradorTareas);
    }

    @FXML private void configuracionRed() {
        ejecutarAccion("Configuración de red", "Abre la configuración de red de Windows.",
                "Abriendo la configuración de red...", reparacionService::abrirConfiguracionRed);
    }

    @FXML private void cargarHistorial() {
        VBox page = crearPagina("Historial del diagnóstico",
                "Últimos diagnósticos guardados en este equipo.");
        Label estado = agregarEstado(page, "Cargando historial...");
        TableView<Diagnostico> tabla = crearTablaDiagnosticos(true);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().add(tabla);
        ejecutarEnSegundoPlano("No se pudo actualizar el historial:",
                diagnosticoService::historial,
                historial -> {
                    tabla.setItems(FXCollections.observableArrayList(historial));
                    estado.setText(historial.size() + " registros.");
                    estadoLabel.setText("Historial actualizado");
                },
                error -> estado.setText(error));
    }

    private void ejecutarAccion(String titulo, String descripcion, String progreso,
                                Supplier<String> accion) {
        VBox page = crearPagina(titulo, descripcion);
        Label estado = agregarEstado(page, progreso);
        Label resumen = new Label("Esperando respuesta de Windows...");
        resumen.setWrapText(true);
        resumen.getStyleClass().add("result-value");
        VBox tarjeta = new VBox(10, new Label("ESTADO DE LA ACCIÓN"), resumen);
        tarjeta.getStyleClass().add("result-card");
        TextArea detalle = new TextArea();
        detalle.setEditable(false);
        detalle.setWrapText(true);
        detalle.getStyleClass().add("console");
        TitledPane detallesTecnicos = new TitledPane("Detalles técnicos de Windows", detalle);
        detallesTecnicos.setExpanded(false);
        VBox.setVgrow(detallesTecnicos, Priority.ALWAYS);
        page.getChildren().addAll(tarjeta, detallesTecnicos);
        ejecutarEnSegundoPlano("No se pudo completar la acción:",
                accion::get,
                salida -> {
                    detalle.setText(salida);
                    if (esErrorComando(salida)) {
                        resumen.setText("Windows no pudo completar la acción. Consulta el detalle técnico.");
                        estado.setText("La acción no se completó.");
                        detallesTecnicos.setExpanded(true);
                    } else {
                        resumen.setText(resumenAccion(titulo));
                        estado.setText("Acción finalizada.");
                    }
                    estadoLabel.setText(estado.getText());
                },
                error -> {
                    detalle.setText(error);
                    resumen.setText("Ocurrió un error. Consulta el detalle técnico.");
                    estado.setText("La acción no se completó.");
                    detallesTecnicos.setExpanded(true);
                    estadoLabel.setText("Error");
                });
    }

    private void mostrarTablaDatos(String titulo, String descripcion, String progreso,
                                   List<String> encabezados, Callable<String> tarea) {
        VBox page = crearPagina(titulo, descripcion);
        Label estado = agregarEstado(page, progreso);
        TableView<List<String>> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("No hay elementos para mostrar."));
        for (int indice = 0; indice < encabezados.size(); indice++) {
            final int columna = indice;
            TableColumn<List<String>, String> tableColumn = new TableColumn<>(encabezados.get(indice));
            tableColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                    columna < cell.getValue().size() ? cell.getValue().get(columna) : ""));
            tabla.getColumns().add(tableColumn);
        }
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().addAll(new Label("Elementos encontrados"), tabla);
        ejecutarEnSegundoPlano("No se pudo completar la acción:",
                tarea,
                salida -> {
                    if (esErrorComando(salida)) {
                        estado.setText("No se pudieron cargar los datos.");
                        estadoLabel.setText("Error");
                        tabla.setPlaceholder(new Label(salida));
                        return;
                    }
                    if (salida.startsWith("Windows no reportó programas")) {
                        estado.setText("No hay programas de inicio registrados.");
                        estadoLabel.setText("Consulta finalizada");
                        return;
                    }
                    List<List<String>> filas = analizarCsv(salida);
                    tabla.setItems(FXCollections.observableArrayList(filas));
                    estado.setText(filas.size() + (filas.size() == 1
                            ? " elemento encontrado." : " elementos encontrados."));
                    estadoLabel.setText(estado.getText());
                },
                error -> {
                    tabla.setPlaceholder(new Label(error));
                    estado.setText("Error al cargar los datos.");
                    estadoLabel.setText("Error");
                });
    }

    private List<List<String>> analizarCsv(String csv) {
        if (csv.isBlank() || csv.equals("Comando ejecutado correctamente sin salida.")) {
            return List.of();
        }
        List<List<String>> filas = new ArrayList<>();
        for (String linea : csv.split("\\R")) {
            if (!linea.isBlank()) {
                filas.add(analizarLineaCsv(linea));
            }
        }
        return filas;
    }

    private List<String> analizarLineaCsv(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int indice = 0; indice < linea.length(); indice++) {
            char caracter = linea.charAt(indice);
            if (caracter == '"' && entreComillas && indice + 1 < linea.length()
                    && linea.charAt(indice + 1) == '"') {
                campo.append('"');
                indice++;
            } else if (caracter == '"') {
                entreComillas = !entreComillas;
            } else if (caracter == ',' && !entreComillas) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(caracter);
            }
        }
        campos.add(campo.toString());
        return campos;
    }

    private String resumenAccion(String titulo) {
        return switch (titulo) {
            case "Limpiar DNS" -> "La solicitud para vaciar la caché DNS terminó sin errores.";
            case "Renovar dirección IP" -> "La solicitud para renovar la dirección IP terminó sin errores.";
            case "Restablecer Winsock" ->
                    "Winsock se restableció. Reinicia Windows para aplicar los cambios.";
            case "Comprobación de archivos de Windows" ->
                    "SFC terminó. Consulta los detalles para ver el resultado de la comprobación.";
            case "Reparar imagen de Windows" ->
                    "DISM terminó. Consulta los detalles para ver el resultado de la reparación.";
            case "Limpieza de disco" -> "Se solicitó abrir la herramienta Limpieza de disco.";
            case "Administrador de tareas" -> "Se solicitó abrir el Administrador de tareas.";
            case "Configuración de red" -> "Se solicitó abrir la configuración de red de Windows.";
            default -> "La acción terminó sin errores reportados por Windows.";
        };
    }

    private void abrirReporte(Path reporte, Label estado) {
        try {
            Desktop.getDesktop().open(reporte.toFile());
            estado.setText("Reporte abierto.");
            estadoLabel.setText("Reporte PDF abierto");
        } catch (IOException | UnsupportedOperationException e) {
            estado.setText("No se pudo abrir el reporte: " + e.getMessage());
            estadoLabel.setText("Error al abrir el reporte");
        }
    }

    private boolean puedeAbrirArchivos() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    private VBox crearPagina(String titulo, String descripcion) {
        Label encabezado = new Label(titulo);
        encabezado.getStyleClass().add("page-title");
        Label detalle = new Label(descripcion);
        detalle.getStyleClass().add("page-description");
        detalle.setWrapText(true);
        VBox page = new VBox(14, encabezado, detalle);
        page.getStyleClass().add("content");
        pageRoot.getChildren().setAll(page);
        VBox.setVgrow(page, Priority.ALWAYS);
        return page;
    }

    private Label agregarEstado(VBox page, String texto) {
        Label estado = new Label(texto);
        estado.getStyleClass().add("page-status");
        page.getChildren().add(estado);
        estadoLabel.setText(texto);
        return estado;
    }

    private TableView<Diagnostico> crearTablaDiagnosticos(boolean incluirFecha) {
        TableView<Diagnostico> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        if (incluirFecha) {
            TableColumn<Diagnostico, String> fecha = new TableColumn<>("Fecha");
            fecha.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    c.getValue().getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            fecha.setPrefWidth(145);
            tabla.getColumns().add(fecha);
        }
        TableColumn<Diagnostico, String> categoria = new TableColumn<>("Área");
        categoria.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getCategoria()));
        TableColumn<Diagnostico, String> resultado = new TableColumn<>("Estado");
        resultado.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getResultado()));
        TableColumn<Diagnostico, String> detalle = new TableColumn<>("Detalle");
        detalle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getDetalle().replace(System.lineSeparator(), " ")));
        tabla.getColumns().add(categoria);
        tabla.getColumns().add(resultado);
        tabla.getColumns().add(detalle);
        return tabla;
    }

    private String formatearProblema(Problema problema) {
        return problema.area() + " | " + problema.severidad() + System.lineSeparator()
                + problema.problema() + System.lineSeparator()
                + "Recomendación: " + problema.recomendacion();
    }

    private boolean esErrorComando(String resultado) {
        return resultado.startsWith("No se pudo") ||
                resultado.startsWith("El comando terminó con código") ||
                resultado.startsWith("El comando superó el tiempo") ||
                resultado.startsWith("La ejecución del comando fue interrumpida");
    }

    private <T> void ejecutarEnSegundoPlano(String mensajeError, Callable<T> tarea,
                                            Consumer<T> alCompletar,
                                            Consumer<String> alFallar) {
        backgroundExecutor.submit(() -> {
            try {
                T resultado = tarea.call();
                Platform.runLater(() -> alCompletar.accept(resultado));
            } catch (Exception e) {
                String detalle = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                Platform.runLater(() -> {
                    String error = mensajeError + " " + detalle;
                    alFallar.accept(error);
                    estadoLabel.setText("Error");
                });
            }
        });
    }

    @PreDestroy
    private void detenerTrabajosEnSegundoPlano() {
        backgroundExecutor.shutdownNow();
    }

    private boolean confirmar(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje,
                ButtonType.CANCEL, ButtonType.OK);
        alerta.setTitle("PC Doctor");
        alerta.setHeaderText(titulo);
        return alerta.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static final class CandidatoDuplicado {
        private final Path archivo;
        private final Path original;
        private final SimpleBooleanProperty seleccionado = new SimpleBooleanProperty(true);

        private CandidatoDuplicado(Path archivo, Path original) {
            this.archivo = archivo;
            this.original = original;
        }

        private Path archivo() {
            return archivo;
        }

        private Path original() {
            return original;
        }

        private boolean seleccionado() {
            return seleccionado.get();
        }

        private SimpleBooleanProperty seleccionadoProperty() {
            return seleccionado;
        }
    }

    private record TrashSummary(List<Path> movidos, List<String> errores) {
    }
}
