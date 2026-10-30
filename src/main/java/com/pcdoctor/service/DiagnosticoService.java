package com.pcdoctor.service;

import com.pcdoctor.model.Diagnostico;
import com.pcdoctor.model.Problema;
import com.pcdoctor.repository.DiagnosticoRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DiagnosticoService {
    private static final Duration ANALYSIS_COMMAND_TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern TEMPERATURA_CELSIUS = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*°C");

    private final DiagnosticoRepository repository;
    private final ComandoWindowsService comandos;

    public DiagnosticoService(DiagnosticoRepository repository, ComandoWindowsService comandos) {
        this.repository = repository;
        this.comandos = comandos;
    }

    public List<Diagnostico> ejecutarDiagnostico() {
        return analizarYGuardar().diagnosticos();
    }

    public AnalisisCompleto analizarYGuardar() {
        AnalisisCompleto analisis = analizar();
        List<Diagnostico> guardados = analisis.diagnosticos().stream()
                .map(d -> repository.save(d))
                .toList();
        return new AnalisisCompleto(
                analisis.sistema(), analisis.almacenamiento(), guardados, analisis.problemas());
    }

    public List<Problema> detectarProblemas() {
        return analizar().problemas();
    }

    public List<Diagnostico> historial() {
        return repository.findTop100ByOrderByFechaDesc();
    }

    public String obtenerProcesos() {
        String systemRoot = System.getenv("SystemRoot");
        String tasklist = systemRoot == null || systemRoot.isBlank()
                ? "tasklist.exe"
                : new File(systemRoot, "System32\\tasklist.exe").getPath();
        return comandos.ejecutar(ANALYSIS_COMMAND_TIMEOUT, tasklist, "/fo", "csv", "/nh");
    }

    public String obtenerInicioWindows() {
        String powershell = "Get-CimInstance Win32_StartupCommand | " +
                "Select-Object Name, Command, Location, User | ConvertTo-Csv -NoTypeInformation";
        String salida = comandos.ejecutar(ANALYSIS_COMMAND_TIMEOUT, "powershell", "-NoProfile",
                "-NonInteractive", "-Command", powershell);
        if (salida.equals("Comando ejecutado correctamente sin salida.")) {
            return "Windows no reportó programas configurados para iniciarse automáticamente.";
        }
        return salida;
    }

    public String resumenSistema() {
        return obtenerSistema() + System.lineSeparator() + System.lineSeparator() +
                obtenerDisco();
    }

    private AnalisisCompleto analizar() {
        String sistema = obtenerSistema();
        String memoria = obtenerMemoria();
        String estadoMemoria = evaluarMemoria();
        String almacenamiento = obtenerDisco();
        String estadoDisco = evaluarDisco();
        String pruebaRed = comandos.ejecutar(ANALYSIS_COMMAND_TIMEOUT,
                "cmd", "/c", "ping", "-n", "1", "-w", "3000", "8.8.8.8");
        String estadoRed = evaluarRed(pruebaRed);
        String detalleRed = estadoRed + System.lineSeparator() + obtenerRed();
        List<Diagnostico> resultados = List.of(
                new Diagnostico("Sistema", "OK", sistema),
                new Diagnostico("Memoria", estadoMemoria, memoria),
                new Diagnostico("Disco", estadoDisco, almacenamiento),
                new Diagnostico("Red", estadoRed, detalleRed));

        List<Problema> problemas = new ArrayList<>();
        if ("REVISAR".equals(estadoDisco)) {
            problemas.add(new Problema("Disco", "Alta",
                    "Alguna unidad supera el 90% de espacio usado.",
                    "Liberar espacio y revisar archivos grandes."));
        }
        if ("REVISAR".equals(estadoMemoria)) {
            problemas.add(new Problema("Memoria", "Media",
                    "La memoria física disponible es inferior al 10%.",
                    "Cerrar aplicaciones y comprobar los procesos con mayor consumo."));
        }
        if ("REVISAR".equals(estadoRed)) {
            problemas.add(new Problema("Red", "Media",
                    "No se recibió respuesta del host de prueba 8.8.8.8.",
                    "Comprobar la conexión. Algunos firewalls bloquean las solicitudes ping."));
        }
        if (problemas.isEmpty()) {
            problemas.add(new Problema("General", "OK", "No se detectaron alertas básicas.",
                    "Mantener Windows actualizado y realizar copias de seguridad."));
        }
        return new AnalisisCompleto(sistema, almacenamiento, resultados, problemas);
    }

    private String obtenerSistema() {
        return "SO: " + System.getProperty("os.name") +
                " | Versión: " + System.getProperty("os.version") +
                " | Arquitectura: " + System.getProperty("os.arch") +
                " | Java: " + System.getProperty("java.version") +
                " | Procesadores lógicos: " + Runtime.getRuntime().availableProcessors();
    }

    private String obtenerMemoria() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean memoriaFisica) {
            long total = memoriaFisica.getTotalMemorySize();
            long disponible = memoriaFisica.getFreeMemorySize();
            if (total > 0) {
                long usada = total - disponible;
                int porcentaje = (int) (usada * 100 / total);
                return "RAM física: " + mb(usada) + " MB usados de " + mb(total) +
                        " MB (" + porcentaje + "%); disponibles: " + mb(disponible) + " MB.";
            }
        }
        return "RAM física: Windows no permitió obtener la capacidad total y disponible.";
    }

    private String evaluarMemoria() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean memoriaFisica) {
            long total = memoriaFisica.getTotalMemorySize();
            long disponible = memoriaFisica.getFreeMemorySize();
            if (total > 0 && disponible * 100 / total < 10) return "REVISAR";
            if (total > 0) return "OK";
        }
        return "INFO";
    }

    private String obtenerDisco() {
        StringBuilder sb = new StringBuilder();
        for (File root : File.listRoots()) {
            try {
                FileStore store = Files.getFileStore(root.toPath());
                long total = store.getTotalSpace();
                long libre = store.getUsableSpace();
                long usado = total - libre;
                int porcentaje = total > 0 ? (int) (usado * 100 / total) : 0;
                sb.append(root).append("  |  ").append(porcentaje).append("% usado  |  ")
                        .append(mb(usado)).append(" MB usados de ").append(mb(total))
                        .append(" MB  |  ").append(mb(libre)).append(" MB libres")
                        .append(System.lineSeparator());
            } catch (Exception e) {
                sb.append(root).append("  |  No se pudo consultar: ").append(e.getMessage())
                        .append(System.lineSeparator());
            }
        }
        return sb.toString().strip();
    }

    private String evaluarDisco() {
        boolean consultado = false;
        for (File root : File.listRoots()) {
            try {
                long total = root.getTotalSpace();
                if (total > 0) {
                    consultado = true;
                    long usado = total - root.getUsableSpace();
                    if (usado * 100 / total >= 90) return "REVISAR";
                }
            } catch (SecurityException ignored) {
                // Una unidad sin permisos no debe impedir la revisión de las demás.
            }
        }
        return consultado ? "OK" : "INFO";
    }

    private String obtenerRed() {
        return comandos.ejecutar(ANALYSIS_COMMAND_TIMEOUT, "cmd", "/c", "ipconfig", "/all");
    }

    private String evaluarRed(String salida) {
        if (salida.startsWith("No se pudo") || salida.startsWith("El comando superó")) return "INFO";
        return salida.toLowerCase(Locale.ROOT).contains("ttl=") ? "OK" : "REVISAR";
    }

    private Double temperaturaMaxima(String detalle) {
        Matcher matcher = TEMPERATURA_CELSIUS.matcher(detalle);
        Double maxima = null;
        while (matcher.find()) {
            double valor = Double.parseDouble(matcher.group(1).replace(',', '.'));
            maxima = maxima == null ? valor : Math.max(maxima, valor);
        }
        return maxima;
    }

    private long mb(long bytes) {
        return bytes / 1024 / 1024;
    }
}
