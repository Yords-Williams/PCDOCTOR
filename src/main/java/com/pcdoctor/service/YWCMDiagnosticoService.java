package com.pcdoctor.service;

import com.pcdoctor.model.YWCMDiagnostico;
import com.pcdoctor.model.YWCMProblema;
import com.pcdoctor.repository.YWCMDiagnosticoRepository;
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
public class YWCMDiagnosticoService {
    private static final Duration YWCMANALYSIS_COMMAND_TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern YWCMTEMPERATURA_CELSIUS = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*°C");

    private final YWCMDiagnosticoRepository YWCMrepository;
    private final YWCMComandoWindowsService YWCMcomandos;

    public YWCMDiagnosticoService(YWCMDiagnosticoRepository YWCMrepository, YWCMComandoWindowsService YWCMcomandos) {
        this.YWCMrepository = YWCMrepository;
        this.YWCMcomandos = YWCMcomandos;
    }

    public List<YWCMDiagnostico> YWCMejecutarDiagnostico() {
        return YWCManalizarYGuardar().diagnosticos();
    }

    public YWCMAnalisisCompleto YWCManalizarYGuardar() {
        YWCMAnalisisCompleto analisis = YWCManalizar();
        List<YWCMDiagnostico> guardados = analisis.diagnosticos().stream()
                .map(d -> YWCMrepository.save(d))
                .toList();
        return new YWCMAnalisisCompleto(
                analisis.sistema(), analisis.almacenamiento(), guardados, analisis.problemas());
    }

    public List<YWCMProblema> YWCMdetectarProblemas() {
        return YWCManalizar().problemas();
    }

    public List<YWCMDiagnostico> YWCMhistorial() {
        return YWCMrepository.findTop100ByOrderByYWCMfechaDesc();
    }

    public String YWCMobtenerProcesos() {
        String systemRoot = System.getenv("SystemRoot");
        String tasklist = systemRoot == null || systemRoot.isBlank()
                ? "tasklist.exe"
                : new File(systemRoot, "System32\\tasklist.exe").getPath();
        return YWCMcomandos.YWCMejecutar(YWCMANALYSIS_COMMAND_TIMEOUT, tasklist, "/fo", "csv", "/nh");
    }

    public String YWCMobtenerInicioWindows() {
        String powershell = "Get-CimInstance Win32_StartupCommand | " +
                "Select-Object Name, Command, Location, User | ConvertTo-Csv -NoTypeInformation";
        String salida = YWCMcomandos.YWCMejecutar(YWCMANALYSIS_COMMAND_TIMEOUT, "powershell", "-NoProfile",
                "-NonInteractive", "-Command", powershell);
        if (salida.equals("Comando ejecutado correctamente sin salida.")) {
            return "Windows no reportó programas configurados para iniciarse automáticamente.";
        }
        return salida;
    }

    public String YWCMresumenSistema() {
        return YWCMobtenerSistema() + System.lineSeparator() + System.lineSeparator() +
                YWCMobtenerDisco();
    }

    private YWCMAnalisisCompleto YWCManalizar() {
        String sistema = YWCMobtenerSistema();
        String memoria = YWCMobtenerMemoria();
        String estadoMemoria = YWCMevaluarMemoria();
        String almacenamiento = YWCMobtenerDisco();
        String estadoDisco = YWCMevaluarDisco();
        String pruebaRed = YWCMcomandos.YWCMejecutar(YWCMANALYSIS_COMMAND_TIMEOUT,
                "cmd", "/c", "ping", "-n", "1", "-w", "3000", "8.8.8.8");
        String estadoRed = YWCMevaluarRed(pruebaRed);
        String detalleRed = estadoRed + System.lineSeparator() + YWCMobtenerRed();
        List<YWCMDiagnostico> resultados = List.of(
                new YWCMDiagnostico("Sistema", "OK", sistema),
                new YWCMDiagnostico("Memoria", estadoMemoria, memoria),
                new YWCMDiagnostico("Disco", estadoDisco, almacenamiento),
                new YWCMDiagnostico("Red", estadoRed, detalleRed));

        List<YWCMProblema> problemas = new ArrayList<>();
        if ("REVISAR".equals(estadoDisco)) {
            problemas.add(new YWCMProblema("Disco", "Alta",
                    "Alguna unidad supera el 90% de espacio usado.",
                    "Liberar espacio y revisar archivos grandes."));
        }
        if ("REVISAR".equals(estadoMemoria)) {
            problemas.add(new YWCMProblema("Memoria", "Media",
                    "La memoria física disponible es inferior al 10%.",
                    "Cerrar aplicaciones y comprobar los procesos con mayor consumo."));
        }
        if ("REVISAR".equals(estadoRed)) {
            problemas.add(new YWCMProblema("Red", "Media",
                    "No se recibió respuesta del host de prueba 8.8.8.8.",
                    "Comprobar la conexión. Algunos firewalls bloquean las solicitudes ping."));
        }
        if (problemas.isEmpty()) {
            problemas.add(new YWCMProblema("General", "OK", "No se detectaron alertas básicas.",
                    "Mantener Windows actualizado y realizar copias de seguridad."));
        }
        return new YWCMAnalisisCompleto(sistema, almacenamiento, resultados, problemas);
    }

    private String YWCMobtenerSistema() {
        return "SO: " + System.getProperty("os.name") +
                " | Versión: " + System.getProperty("os.version") +
                " | Arquitectura: " + System.getProperty("os.arch") +
                " | Java: " + System.getProperty("java.version") +
                " | Procesadores lógicos: " + Runtime.getRuntime().availableProcessors();
    }

    private String YWCMobtenerMemoria() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean memoriaFisica) {
            long total = memoriaFisica.getTotalMemorySize();
            long disponible = memoriaFisica.getFreeMemorySize();
            if (total > 0) {
                long usada = total - disponible;
                int porcentaje = (int) (usada * 100 / total);
                return "RAM física: " + YWCMmb(usada) + " MB usados de " + YWCMmb(total) +
                        " MB (" + porcentaje + "%); disponibles: " + YWCMmb(disponible) + " MB.";
            }
        }
        return "RAM física: Windows no permitió obtener la capacidad total y disponible.";
    }

    private String YWCMevaluarMemoria() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean memoriaFisica) {
            long total = memoriaFisica.getTotalMemorySize();
            long disponible = memoriaFisica.getFreeMemorySize();
            if (total > 0 && disponible * 100 / total < 10) return "REVISAR";
            if (total > 0) return "OK";
        }
        return "INFO";
    }

    private String YWCMobtenerDisco() {
        StringBuilder sb = new StringBuilder();
        for (File root : File.listRoots()) {
            try {
                FileStore store = Files.getFileStore(root.toPath());
                long total = store.getTotalSpace();
                long libre = store.getUsableSpace();
                long usado = total - libre;
                int porcentaje = total > 0 ? (int) (usado * 100 / total) : 0;
                sb.append(root).append("  |  ").append(porcentaje).append("% usado  |  ")
                        .append(YWCMmb(usado)).append(" MB usados de ").append(YWCMmb(total))
                        .append(" MB  |  ").append(YWCMmb(libre)).append(" MB libres")
                        .append(System.lineSeparator());
            } catch (Exception e) {
                sb.append(root).append("  |  No se pudo consultar: ").append(e.getMessage())
                        .append(System.lineSeparator());
            }
        }
        return sb.toString().strip();
    }

    private String YWCMevaluarDisco() {
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

    private String YWCMobtenerRed() {
        return YWCMcomandos.YWCMejecutar(YWCMANALYSIS_COMMAND_TIMEOUT, "cmd", "/c", "ipconfig", "/all");
    }

    private String YWCMevaluarRed(String salida) {
        if (salida.startsWith("No se pudo") || salida.startsWith("El comando superó")) return "INFO";
        return salida.toLowerCase(Locale.ROOT).contains("ttl=") ? "OK" : "REVISAR";
    }

    private Double YWCMtemperaturaMaxima(String YWCMdetalle) {
        Matcher matcher = YWCMTEMPERATURA_CELSIUS.matcher(YWCMdetalle);
        Double maxima = null;
        while (matcher.find()) {
            double valor = Double.parseDouble(matcher.group(1).replace(',', '.'));
            maxima = maxima == null ? valor : Math.max(maxima, valor);
        }
        return maxima;
    }

    private long YWCMmb(long bytes) {
        return bytes / 1024 / 1024;
    }
}
