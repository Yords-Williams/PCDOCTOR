package com.pcdoctor.controller;

import com.pcdoctor.model.YWCMDiagnostico;
import com.pcdoctor.model.YWCMProblema;
import com.pcdoctor.service.YWCMAntivirusService;
import com.pcdoctor.service.YWCMArchivoDuplicadoService;
import com.pcdoctor.service.YWCMDiagnosticoService;
import com.pcdoctor.service.YWCMReparacionService;
import com.pcdoctor.service.YWCMReportePdfService;
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
import java.nio.file.Files;
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
public class YWCMDashboardController {
    @FXML private VBox pageRoot;
    @FXML private Label estadoLabel;

    private final YWCMDiagnosticoService YWCMdiagnosticoService;
    private final YWCMReparacionService YWCMreparacionService;
    private final YWCMReportePdfService YWCMreportePdfService;
    private final YWCMAntivirusService YWCMantivirusService;
    private final YWCMArchivoDuplicadoService YWCMarchivoDuplicadoService;
    private final ExecutorService YWCMbackgroundExecutor = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "pcdoctor-background");
        thread.setDaemon(true);
        return thread;
    });

    public YWCMDashboardController(YWCMDiagnosticoService YWCMdiagnosticoService,
                               YWCMReparacionService YWCMreparacionService,
                               YWCMReportePdfService YWCMreportePdfService,
                               YWCMAntivirusService YWCMantivirusService,
                               YWCMArchivoDuplicadoService YWCMarchivoDuplicadoService) {
        this.YWCMdiagnosticoService = YWCMdiagnosticoService;
        this.YWCMreparacionService = YWCMreparacionService;
        this.YWCMreportePdfService = YWCMreportePdfService;
        this.YWCMantivirusService = YWCMantivirusService;
        this.YWCMarchivoDuplicadoService = YWCMarchivoDuplicadoService;
    }

    @FXML public void initialize() {
        mostrarPaginaInicio();
    }

    @FXML private void mostrarPaginaInicio() {
        VBox page = YWCMcrearPagina("Bienvenido a PC Doctor",
                "Selecciona una herramienta en el menú para abrir su pantalla.");
        Label ayuda = new Label("Cada opción muestra aquí sus propios resultados y controles.");
        ayuda.getStyleClass().add("page-card");
        page.getChildren().add(ayuda);
        estadoLabel.setText("Listo");
    }

    @FXML private void YWCMejecutarDiagnostico() {
        VBox page = YWCMcrearPagina("Diagnóstico completo",
                "Revisa el sistema, memoria, almacenamiento, red y temperatura.");
        Label estado = YWCMagregarEstado(page, "Analizando el equipo...");
        TableView<YWCMDiagnostico> tabla = YWCMcrearTablaDiagnosticos(false);
        ListView<String> problemas = new ListView<>();
        problemas.setPlaceholder(new Label("No hay recomendaciones para mostrar."));
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().addAll(new Label("Resultados del análisis"), tabla,
                new Label("Problemas y recomendaciones"), problemas);
        YWCMejecutarEnSegundoPlano("No se pudo completar el diagnóstico:",
                YWCMdiagnosticoService::YWCManalizarYGuardar,
                YWCMresultado -> {
                    tabla.setItems(FXCollections.observableArrayList(YWCMresultado.diagnosticos()));
                    problemas.setItems(FXCollections.observableArrayList(
                            YWCMresultado.problemas().stream()
                                    .map(this::YWCMformatearProblema)
                                    .toList()));
                    long revisar = YWCMresultado.diagnosticos().stream()
                            .filter(d -> "REVISAR".equals(d.YWCMgetResultado())).count();
                    estado.setText(YWCMresultado.diagnosticos().size() + " áreas revisadas; "
                            + revisar + " requieren atención.");
                    estadoLabel.setText(revisar == 0 ? "Diagnóstico terminado"
                            : "Diagnóstico terminado con alertas");
                },
                error -> estado.setText(error));
    }

    @FXML private void YWCMdetectarProblemas() {
        VBox page = YWCMcrearPagina("Problemas detectados",
                "Alertas y recomendaciones generadas a partir del estado actual del equipo.");
        Label estado = YWCMagregarEstado(page, "Buscando problemas...");
        ListView<String> lista = new ListView<>();
        lista.setPlaceholder(new Label("No hay recomendaciones para mostrar."));
        VBox.setVgrow(lista, Priority.ALWAYS);
        page.getChildren().add(lista);
        YWCMejecutarEnSegundoPlano("No se pudieron detectar los problemas:",
                YWCMdiagnosticoService::YWCMdetectarProblemas,
                problemas -> {
                    lista.setItems(FXCollections.observableArrayList(
                            problemas.stream().map(this::YWCMformatearProblema).toList()));
                    estado.setText(problemas.size() + " recomendaciones.");
                    estadoLabel.setText("Detección terminada");
                },
                error -> estado.setText(error));
    }

    @FXML private void mostrarProcesos() {
        YWCMmostrarTablaDatos("Procesos activos", "Procesos que se están ejecutando en Windows.",
                "Consultando procesos...",
                List.of("Imagen", "PID", "Sesión", "N.º sesión", "Memoria"),
                YWCMdiagnosticoService::YWCMobtenerProcesos);
    }

    @FXML private void mostrarInicio() {
        YWCMmostrarTablaDatos("Programas de inicio",
                "Programas configurados para iniciarse automáticamente con Windows.",
                "Consultando programas de inicio...",
                List.of("Nombre", "Comando", "Ubicación", "Usuario"),
                YWCMdiagnosticoService::YWCMobtenerInicioWindows);
    }

    @FXML private void mostrarAntivirus() {
        VBox page = YWCMcrearPagina("Análisis antivirus",
                "PC Doctor Soporte solo ofrece una protección básica y no es en tiempo real.");
        Label estado = YWCMagregarEstado(page, "Selecciona una carpeta para YWCManalizar.");
        Label carpeta = new Label("Ninguna carpeta seleccionada.");
        carpeta.setWrapText(true);
        Button YWCManalizar = new Button("Elegir carpeta y YWCManalizar");
        TableView<YWCMAntivirusService.YWCMDetection> detecciones = YWCMcrearTablaAntivirus();
        TextArea incidencias = new TextArea();
        incidencias.setEditable(false);
        incidencias.setWrapText(true);
        incidencias.setPrefRowCount(4);
        VBox.setVgrow(detecciones, Priority.ALWAYS);
        page.getChildren().addAll(carpeta, YWCManalizar,
                new Label("Coincidencias"), detecciones,
                new Label("Detalles del análisis"), incidencias);

        YWCManalizar.setOnAction(event -> {
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle("Selecciona una carpeta para YWCManalizar");
            File seleccion = selector.showDialog(pageRoot.getScene().getWindow());
            if (seleccion == null) return;

            Path directorio = seleccion.toPath();
            carpeta.setText("Carpeta: " + directorio.toAbsolutePath());
            detecciones.getItems().clear();
            incidencias.clear();
            YWCManalizar.setDisable(true);
            estado.setText("Analizando archivos con el motor PC Doctor...");
            estadoLabel.setText(estado.getText());
            YWCMejecutarEnSegundoPlano("No se pudo completar el análisis antivirus:",
                    () -> YWCMantivirusService.YWCMscan(directorio),
                    YWCMresultado -> {
                        detecciones.setItems(FXCollections.observableArrayList(YWCMresultado.detections()));
                        incidencias.setText(YWCMresultado.YWCMoutput());
                        if (YWCMresultado.detections().isEmpty()) {
                            estado.setText("No se encontraron coincidencias en "
                                    + YWCMresultado.filesScanned() + " archivos.");
                            estadoLabel.setText("Análisis finalizado");
                        } else {
                            estado.setText(YWCMresultado.detections().size()
                                    + " coincidencia(s) encontrada(s).");
                            estadoLabel.setText("Se encontraron coincidencias");
                        }
                        YWCManalizar.setDisable(false);
                    },
                    error -> {
                        estado.setText(error);
                        estadoLabel.setText("Error en análisis antivirus");
                        YWCManalizar.setDisable(false);
                    });
        });
    }

    private TableView<YWCMAntivirusService.YWCMDetection> YWCMcrearTablaAntivirus() {
        TableView<YWCMAntivirusService.YWCMDetection> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Todavía no hay detecciones."));
        TableColumn<YWCMAntivirusService.YWCMDetection, String> nombre = new TableColumn<>("Firma");
        nombre.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().name()));
        TableColumn<YWCMAntivirusService.YWCMDetection, String> YWCMarchivo = new TableColumn<>("Archivo");
        YWCMarchivo.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().file().toString()));
        tabla.getColumns().add(nombre);
        tabla.getColumns().add(YWCMarchivo);
        return tabla;
    }

    @FXML private void buscarDuplicados() {
        VBox page = YWCMcrearPagina("Archivos duplicados",
                "Elige una carpeta. Los archivos se comparan por nombre y peso; se conserva "
                        + "uno de cada grupo y puedes desmarcar los que quieras conservar.");
        Label estado = YWCMagregarEstado(page, "Selecciona una carpeta para empezar.");
        Label carpetaLabel = new Label("Ninguna carpeta seleccionada.");
        carpetaLabel.setWrapText(true);
        Button YWCMbuscar = new Button("Elegir carpeta y YWCMbuscar");
        Button enviarPapelera = new Button("Enviar seleccionados a la Papelera");
        enviarPapelera.setDisable(true);
        TableView<YWCMCandidatoDuplicado> tabla = YWCMcrearTablaDuplicados();
        TextArea errores = new TextArea();
        errores.setEditable(false);
        errores.setWrapText(true);
        errores.setPrefRowCount(4);
        errores.setPromptText("Los errores al mover archivos aparecerán aquí.");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().addAll(carpetaLabel, YWCMbuscar, tabla, enviarPapelera, errores);

        YWCMbuscar.setOnAction(event -> {
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle("Selecciona una carpeta para YWCMbuscar duplicados");
            File seleccion = selector.showDialog(pageRoot.getScene().getWindow());
            if (seleccion == null) {
                return;
            }
            Path directorio = seleccion.toPath();
            carpetaLabel.setText("Carpeta: " + directorio.toAbsolutePath());
            tabla.getItems().clear();
            enviarPapelera.setDisable(true);
            YWCMbuscar.setDisable(true);
            errores.clear();
            estado.setText("Buscando archivos duplicados...");
            estadoLabel.setText(estado.getText());
            YWCMejecutarEnSegundoPlano("No se pudo YWCManalizar la carpeta:",
                    () -> YWCMarchivoDuplicadoService.YWCMbuscar(directorio),
                    grupos -> {
                        List<YWCMCandidatoDuplicado> candidatos = grupos.stream()
                                .flatMap(grupo -> grupo.duplicados().stream()
                                        .map(duplicado -> new YWCMCandidatoDuplicado(
                                                duplicado, grupo.YWCMoriginal())))
                                .toList();
                        tabla.setItems(FXCollections.observableArrayList(candidatos));
                        estado.setText(candidatos.isEmpty()
                                ? "No se encontraron archivos duplicados."
                                : candidatos.size() + " duplicados encontrados. "
                                + "Desmarca lo que no quieras mover a la Papelera.");
                        estadoLabel.setText("Búsqueda de duplicados finalizada");
                        enviarPapelera.setDisable(candidatos.isEmpty() || !YWCMpuedeEnviarPapelera());
                        YWCMbuscar.setDisable(false);
                        if (!candidatos.isEmpty() && !YWCMpuedeEnviarPapelera()) {
                            errores.setText("Esta versión de Java no permite enviar archivos "
                                    + "a la Papelera desde la aplicación.");
                        }
                    },
                    error -> {
                        estado.setText(error);
                        estadoLabel.setText("Error");
                        YWCMbuscar.setDisable(false);
                    });
        });

        enviarPapelera.setOnAction(event -> {
            List<YWCMCandidatoDuplicado> seleccionados = tabla.getItems().stream()
                    .filter(YWCMCandidatoDuplicado::YWCMseleccionado)
                    .toList();
            if (seleccionados.isEmpty()) {
                estado.setText("Marca al menos un duplicado para continuar.");
                return;
            }
            if (!YWCMconfirmar("Enviar duplicados a la Papelera",
                    "Se enviarán " + seleccionados.size()
                            + " archivos seleccionados a la Papelera. El YWCMarchivo YWCMoriginal "
                            + "de cada grupo se conservará. ¿Continuar?")) {
                return;
            }

            enviarPapelera.setDisable(true);
            YWCMbuscar.setDisable(true);
            estado.setText("Enviando archivos a la Papelera...");
            estadoLabel.setText(estado.getText());
            YWCMejecutarEnSegundoPlano("No se pudieron mover los archivos:",
                    () -> YWCMmoverAPapelera(seleccionados),
                    resumen -> {
                        List<Path> movidos = resumen.movidos();
                        tabla.getItems().removeIf(candidato -> movidos.contains(candidato.YWCMarchivo()));
                        errores.setText(String.join(System.lineSeparator(), resumen.errores()));
                        estado.setText(movidos.size() + " YWCMarchivo(s) enviado(s) a la Papelera; "
                                + resumen.errores().size() + " error(es).");
                        estadoLabel.setText(resumen.errores().isEmpty()
                                ? "Limpieza finalizada" : "Limpieza finalizada con errores");
                        YWCMbuscar.setDisable(false);
                        enviarPapelera.setDisable(tabla.getItems().isEmpty()
                                || !YWCMpuedeEnviarPapelera());
                    },
                    error -> {
                        errores.setText(error);
                        estado.setText("No se pudieron mover los archivos.");
                        estadoLabel.setText("Error");
                        YWCMbuscar.setDisable(false);
                        enviarPapelera.setDisable(false);
                    });
        });
    }

    private TableView<YWCMCandidatoDuplicado> YWCMcrearTablaDuplicados() {
        TableView<YWCMCandidatoDuplicado> tabla = new TableView<>();
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Selecciona una carpeta para comenzar."));

        TableColumn<YWCMCandidatoDuplicado, Boolean> seleccionar = new TableColumn<>("Enviar");
        seleccionar.setCellValueFactory(c -> c.getValue().YWCMseleccionadoProperty());
        seleccionar.setCellFactory(CheckBoxTableCell.forTableColumn(seleccionar));
        seleccionar.setEditable(true);
        seleccionar.setMaxWidth(90);

        TableColumn<YWCMCandidatoDuplicado, String> YWCMarchivo = new TableColumn<>("Archivo duplicado");
        YWCMarchivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().YWCMarchivo().toString()));
        TableColumn<YWCMCandidatoDuplicado, String> conservar = new TableColumn<>("Se conservará");
        conservar.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().YWCMoriginal().toString()));
        tabla.getColumns().addAll(seleccionar, YWCMarchivo, conservar);
        return tabla;
    }

    private YWCMTrashSummary YWCMmoverAPapelera(List<YWCMCandidatoDuplicado> candidatos) {
        List<Path> movidos = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        for (YWCMCandidatoDuplicado candidato : candidatos) {
            try {
                if (YWCMenviarArchivoAPapelera(candidato.YWCMarchivo())) {
                    movidos.add(candidato.YWCMarchivo());
                } else {
                    errores.add(candidato.YWCMarchivo() + ": Windows no pudo moverlo a la Papelera.");
                }
            } catch (IOException | InterruptedException e) {
                errores.add(candidato.YWCMarchivo() + ": " + e.getMessage());
            }
        }
        return new YWCMTrashSummary(movidos, errores);
    }

    private boolean YWCMenviarArchivoAPapelera(Path YWCMarchivo) throws IOException, InterruptedException {
        String ruta = YWCMarchivo.toAbsolutePath().toString().replace("'", "''");
        String script = "Add-Type -AssemblyName Microsoft.VisualBasic; "
                + "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('" + ruta + "', "
                + "'OnlyErrorDialogs', 'SendToRecycleBin')";
        Process proceso = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-Command", script).redirectErrorStream(true).start();
        return proceso.waitFor() == 0 && !Files.exists(YWCMarchivo);
    }

    private boolean YWCMpuedeEnviarPapelera() {
        return new File(System.getenv("SystemRoot") == null ? "C:\\Windows"
                : System.getenv("SystemRoot"), "System32\\WindowsPowerShell\\v1.0\\powershell.exe").isFile();
    }

    @FXML private void generarReporte() {
        // La carpeta elegida se conserva solo para esta exportación PDF.
        VBox page = YWCMcrearPagina("Reporte de diagnóstico",
                "Selecciona la carpeta donde deseas guardar el informe PDF.");
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle("Seleccionar carpeta para el reporte PDF");
        Path descargas = Path.of(System.getProperty("user.home"), "Downloads");
        if (Files.isDirectory(descargas)) {
            selector.setInitialDirectory(descargas.toFile());
        }
        File carpeta = selector.showDialog(page.getScene().getWindow());
        if (carpeta == null) {
            return;
        }

        Label estado = YWCMagregarEstado(page, "Generando el reporte...");
        Label ruta = new Label("Preparando el informe...");
        ruta.setWrapText(true);
        ruta.getStyleClass().add("result-description");
        Button abrir = new Button("Abrir reporte PDF");
        abrir.setDisable(true);
        VBox tarjeta = new VBox(12, new Label("INFORME PDF"), ruta, abrir);
        tarjeta.getStyleClass().add("result-card");
        page.getChildren().add(tarjeta);
        YWCMejecutarEnSegundoPlano("No se pudo YWCMgenerar el PDF:",
                () -> YWCMreportePdfService.YWCMgenerar(carpeta.toPath()),
                reporte -> {
                    ruta.setText("Guardado en: " + reporte.toAbsolutePath());
                    abrir.setDisable(!YWCMpuedeAbrirArchivos());
                    abrir.setOnAction(event -> YWCMabrirReporte(reporte, estado));
                    estado.setText(YWCMpuedeAbrirArchivos()
                            ? "Reporte generado y listo para abrir."
                            : "Reporte generado; ábrelo desde la ruta indicada.");
                    estadoLabel.setText("Reporte PDF listo");
                },
                error -> {
                    ruta.setText(error);
                    estado.setText("No se pudo YWCMgenerar el reporte.");
                    estadoLabel.setText("Error");
                });
    }

    @FXML private void YWCMrenovarIp() {
        if (YWCMconfirmar("Renovar IP", "Puede desconectar temporalmente la red. ¿Continuar?")) {
            YWCMejecutarAccion("Renovar dirección IP", "Libera y solicita una nueva dirección IP.",
                    "Renovando la dirección IP...", YWCMreparacionService::YWCMrenovarIp);
        }
    }

    @FXML private void YWCMresetWinsock() {
        if (YWCMconfirmar("Restablecer Winsock", "Puede requerir reiniciar Windows. ¿Continuar?")) {
            YWCMejecutarAccion("Restablecer Winsock", "Restablece el catálogo de red de Windows.",
                    "Restableciendo Winsock...", YWCMreparacionService::YWCMresetWinsock);
        }
    }

    @FXML private void cargarHistorial() {
        VBox page = YWCMcrearPagina("Historial del diagnóstico",
                "Últimos diagnósticos guardados en este equipo.");
        Label estado = YWCMagregarEstado(page, "Cargando YWCMhistorial...");
        TableView<YWCMDiagnostico> tabla = YWCMcrearTablaDiagnosticos(true);
        VBox.setVgrow(tabla, Priority.ALWAYS);
        page.getChildren().add(tabla);
        YWCMejecutarEnSegundoPlano("No se pudo actualizar el YWCMhistorial:",
                YWCMdiagnosticoService::YWCMhistorial,
                YWCMhistorial -> {
                    tabla.setItems(FXCollections.observableArrayList(YWCMhistorial));
                    estado.setText(YWCMhistorial.size() + " registros.");
                    estadoLabel.setText("Historial actualizado");
                },
                error -> estado.setText(error));
    }

    private void YWCMejecutarAccion(String YWCMtitulo, String descripcion, String progreso,
                                Supplier<String> accion) {
        VBox page = YWCMcrearPagina(YWCMtitulo, descripcion);
        Label estado = YWCMagregarEstado(page, progreso);
        Label resumen = new Label("Esperando respuesta de Windows...");
        resumen.setWrapText(true);
        resumen.getStyleClass().add("result-value");
        VBox tarjeta = new VBox(10, new Label("ESTADO DE LA ACCIÓN"), resumen);
        tarjeta.getStyleClass().add("result-card");
        TextArea YWCMdetalle = new TextArea();
        YWCMdetalle.setEditable(false);
        YWCMdetalle.setWrapText(true);
        YWCMdetalle.getStyleClass().add("console");
        TitledPane detallesTecnicos = new TitledPane("Detalles técnicos de Windows", YWCMdetalle);
        detallesTecnicos.setExpanded(false);
        VBox.setVgrow(detallesTecnicos, Priority.ALWAYS);
        page.getChildren().addAll(tarjeta, detallesTecnicos);
        YWCMejecutarEnSegundoPlano("No se pudo completar la acción:",
                accion::get,
                salida -> {
                    YWCMdetalle.setText(salida);
                    if (YWCMesErrorComando(salida)) {
                        resumen.setText("Windows no pudo completar la acción. Consulta el YWCMdetalle técnico.");
                        estado.setText("La acción no se completó.");
                        detallesTecnicos.setExpanded(true);
                    } else {
                        resumen.setText(YWCMresumenAccion(YWCMtitulo));
                        estado.setText("Acción finalizada.");
                    }
                    estadoLabel.setText(estado.getText());
                },
                error -> {
                    YWCMdetalle.setText(error);
                    resumen.setText("Ocurrió un error. Consulta el YWCMdetalle técnico.");
                    estado.setText("La acción no se completó.");
                    detallesTecnicos.setExpanded(true);
                    estadoLabel.setText("Error");
                });
    }

    private void YWCMmostrarTablaDatos(String YWCMtitulo, String descripcion, String progreso,
                                   List<String> encabezados, Callable<String> tarea) {
        VBox page = YWCMcrearPagina(YWCMtitulo, descripcion);
        Label estado = YWCMagregarEstado(page, progreso);
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
        YWCMejecutarEnSegundoPlano("No se pudo completar la acción:",
                tarea,
                salida -> {
                    if (YWCMesErrorComando(salida)) {
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
                    List<List<String>> filas = YWCManalizarCsv(salida);
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

    private List<List<String>> YWCManalizarCsv(String csv) {
        if (csv.isBlank() || csv.equals("Comando ejecutado correctamente sin salida.")) {
            return List.of();
        }
        List<List<String>> filas = new ArrayList<>();
        for (String linea : csv.split("\\R")) {
            if (!linea.isBlank()) {
                filas.add(YWCManalizarLineaCsv(linea));
            }
        }
        return filas;
    }

    private List<String> YWCManalizarLineaCsv(String linea) {
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

    private String YWCMresumenAccion(String YWCMtitulo) {
        return switch (YWCMtitulo) {
            case "Limpiar DNS" -> "La solicitud para vaciar la caché DNS terminó sin errores.";
            case "Renovar dirección IP" -> "La solicitud para renovar la dirección IP terminó sin errores.";
            case "Restablecer Winsock" ->
                    "Winsock se restableció. Reinicia Windows para aplicar los cambios.";
            default -> "La acción terminó sin errores reportados por Windows.";
        };
    }

    private void YWCMabrirReporte(Path reporte, Label estado) {
        try {
            Desktop.getDesktop().open(reporte.toFile());
            estado.setText("Reporte abierto.");
            estadoLabel.setText("Reporte PDF abierto");
        } catch (IOException | UnsupportedOperationException e) {
            estado.setText("No se pudo abrir el reporte: " + e.getMessage());
            estadoLabel.setText("Error al abrir el reporte");
        }
    }

    private boolean YWCMpuedeAbrirArchivos() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    private VBox YWCMcrearPagina(String YWCMtitulo, String descripcion) {
        Label encabezado = new Label(YWCMtitulo);
        encabezado.getStyleClass().add("page-title");
        Label YWCMdetalle = new Label(descripcion);
        YWCMdetalle.getStyleClass().add("page-description");
        YWCMdetalle.setWrapText(true);
        VBox page = new VBox(14, encabezado, YWCMdetalle);
        page.getStyleClass().add("content");
        pageRoot.getChildren().setAll(page);
        VBox.setVgrow(page, Priority.ALWAYS);
        return page;
    }

    private Label YWCMagregarEstado(VBox page, String texto) {
        Label estado = new Label(texto);
        estado.getStyleClass().add("page-status");
        page.getChildren().add(estado);
        estadoLabel.setText(texto);
        return estado;
    }

    private TableView<YWCMDiagnostico> YWCMcrearTablaDiagnosticos(boolean incluirFecha) {
        TableView<YWCMDiagnostico> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        if (incluirFecha) {
            TableColumn<YWCMDiagnostico, String> YWCMfecha = new TableColumn<>("Fecha");
            YWCMfecha.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                    c.getValue().YWCMgetFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
            YWCMfecha.setPrefWidth(145);
            tabla.getColumns().add(YWCMfecha);
        }
        TableColumn<YWCMDiagnostico, String> YWCMcategoria = new TableColumn<>("Área");
        YWCMcategoria.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().YWCMgetCategoria()));
        TableColumn<YWCMDiagnostico, String> YWCMresultado = new TableColumn<>("Estado");
        YWCMresultado.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().YWCMgetResultado()));
        TableColumn<YWCMDiagnostico, String> YWCMdetalle = new TableColumn<>("Detalle");
        YWCMdetalle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().YWCMgetDetalle().replace(System.lineSeparator(), " ")));
        tabla.getColumns().add(YWCMcategoria);
        tabla.getColumns().add(YWCMresultado);
        tabla.getColumns().add(YWCMdetalle);
        return tabla;
    }

    private String YWCMformatearProblema(YWCMProblema YWCMproblema) {
        return YWCMproblema.area() + " | " + YWCMproblema.severidad() + System.lineSeparator()
                + YWCMproblema.YWCMproblema() + System.lineSeparator()
                + "Recomendación: " + YWCMproblema.recomendacion();
    }

    private boolean YWCMesErrorComando(String YWCMresultado) {
        return YWCMresultado.startsWith("No se pudo") ||
                YWCMresultado.startsWith("El comando terminó con código") ||
                YWCMresultado.startsWith("El comando superó el tiempo") ||
                YWCMresultado.startsWith("La ejecución del comando fue interrumpida");
    }

    private <T> void YWCMejecutarEnSegundoPlano(String mensajeError, Callable<T> tarea,
                                            Consumer<T> alCompletar,
                                            Consumer<String> alFallar) {
        YWCMbackgroundExecutor.submit(() -> {
            try {
                T YWCMresultado = tarea.call();
                Platform.runLater(() -> alCompletar.accept(YWCMresultado));
            } catch (Exception e) {
                String YWCMdetalle = e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage();
                Platform.runLater(() -> {
                    String error = mensajeError + " " + YWCMdetalle;
                    alFallar.accept(error);
                    estadoLabel.setText("Error");
                });
            }
        });
    }

    @PreDestroy
    private void YWCMdetenerTrabajosEnSegundoPlano() {
        YWCMbackgroundExecutor.shutdownNow();
    }

    private boolean YWCMconfirmar(String YWCMtitulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje,
                ButtonType.CANCEL, ButtonType.OK);
        alerta.setTitle("PC Doctor");
        alerta.setHeaderText(YWCMtitulo);
        return alerta.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static final class YWCMCandidatoDuplicado {
        private final Path YWCMarchivo;
        private final Path YWCMoriginal;
        private final SimpleBooleanProperty YWCMseleccionado = new SimpleBooleanProperty(true);

        private YWCMCandidatoDuplicado(Path YWCMarchivo, Path YWCMoriginal) {
            this.YWCMarchivo = YWCMarchivo;
            this.YWCMoriginal = YWCMoriginal;
        }

        private Path YWCMarchivo() {
            return YWCMarchivo;
        }

        private Path YWCMoriginal() {
            return YWCMoriginal;
        }

        private boolean YWCMseleccionado() {
            return YWCMseleccionado.get();
        }

        private SimpleBooleanProperty YWCMseleccionadoProperty() {
            return YWCMseleccionado;
        }
    }

    private record YWCMTrashSummary(List<Path> movidos, List<String> errores) {
    }
}
