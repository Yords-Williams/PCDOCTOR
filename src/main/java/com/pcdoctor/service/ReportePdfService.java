package com.pcdoctor.service;

import com.pcdoctor.model.Diagnostico;
import com.pcdoctor.model.Problema;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportePdfService {
    private static final float MARGEN = 48;
    private static final float ANCHO_PAGINA = PDRectangle.A4.getWidth();
    private static final float ALTO_PAGINA = PDRectangle.A4.getHeight();
    private static final float ANCHO_CONTENIDO = ANCHO_PAGINA - MARGEN * 2;
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color AZUL = new Color(25, 55, 90);
    private static final Color AZUL_CLARO = new Color(232, 240, 248);
    private static final Color GRIS = new Color(90, 100, 112);

    private final DiagnosticoService diagnosticoService;

    public ReportePdfService(DiagnosticoService diagnosticoService) {
        this.diagnosticoService = diagnosticoService;
    }

    public Path generar() throws Exception {
        AnalisisCompleto analisis = diagnosticoService.analizarYGuardar();
        Path directorio = Path.of(System.getProperty("user.home"), "Downloads");
        Files.createDirectories(directorio);
        LocalDateTime ahora = LocalDateTime.now();
        String marcaTiempo = ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path archivo = directorio.resolve("reporte-" + marcaTiempo + ".pdf");

        try (PDDocument documento = new PDDocument()) {
            Layout layout = new Layout(documento);
            layout.titulo(ahora);

            layout.seccion("Información del equipo");
            layout.parrafo(analisis.sistema(), REGULAR, 10, 15, GRIS);

            layout.seccion("Unidades de almacenamiento");
            for (String unidad : analisis.almacenamiento().split("\\R")) {
                layout.parrafo(unidad, REGULAR, 10, 15, Color.BLACK);
            }

            layout.seccion("Resultados del análisis");
            for (Diagnostico diagnostico : analisis.diagnosticos()) {
                layout.resultado(diagnostico);
            }

            layout.seccion("Alertas y recomendaciones");
            for (Problema problema : analisis.problemas()) {
                layout.problema(problema);
            }

            layout.cerrarPagina();
            agregarPiesDePagina(documento);
            documento.getDocumentInformation().setTitle("PC Doctor - Informe técnico");
            documento.getDocumentInformation().setSubject("Análisis de estado del equipo");
            documento.getDocumentInformation().setCreationDate(
                    java.util.GregorianCalendar.from(ahora.atZone(java.time.ZoneId.systemDefault())));
            documento.save(archivo.toFile());
        }
        return archivo;
    }

    private void agregarPiesDePagina(PDDocument documento) throws IOException {
        int totalPaginas = documento.getNumberOfPages();
        for (int i = 0; i < totalPaginas; i++) {
            PDPage pagina = documento.getPage(i);
            try (PDPageContentStream contenido =
                         new PDPageContentStream(documento, pagina, AppendMode.APPEND, true, true)) {
                contenido.setStrokingColor(new Color(210, 218, 226));
                contenido.moveTo(MARGEN, 37);
                contenido.lineTo(ANCHO_PAGINA - MARGEN, 37);
                contenido.stroke();
                escribir(contenido, REGULAR, 8, MARGEN, 23,
                        "PC Doctor  |  Informe técnico", GRIS);
                String paginaTexto = "Página " + (i + 1) + " de " + totalPaginas;
                float ancho = REGULAR.getStringWidth(paginaTexto) / 1000 * 8;
                escribir(contenido, REGULAR, 8, ANCHO_PAGINA - MARGEN - ancho, 23,
                        paginaTexto, GRIS);
            }
        }
    }

    private static void escribir(PDPageContentStream contenido, PDFont fuente, float tamano,
                                 float x, float y, String texto, Color color) throws IOException {
        contenido.beginText();
        contenido.setFont(fuente, tamano);
        contenido.setNonStrokingColor(color);
        contenido.newLineAtOffset(x, y);
        contenido.showText(limpiar(texto));
        contenido.endText();
    }

    private static String limpiar(String texto) {
        String sinControles = texto.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return new String(sinControles.getBytes(Charset.forName("windows-1252")),
                Charset.forName("windows-1252"));
    }

    private static final class Layout {
        private final PDDocument documento;
        private PDPage pagina;
        private PDPageContentStream contenido;
        private float y;

        private Layout(PDDocument documento) throws IOException {
            this.documento = documento;
            nuevaPagina();
        }

        private void titulo(LocalDateTime fecha) throws IOException {
            contenido.setNonStrokingColor(AZUL);
            contenido.addRect(0, ALTO_PAGINA - 96, ANCHO_PAGINA, 96);
            contenido.fill();
            escribir(contenido, NEGRITA, 22, MARGEN, ALTO_PAGINA - 46,
                    "PC Doctor", Color.WHITE);
            escribir(contenido, REGULAR, 11, MARGEN, ALTO_PAGINA - 66,
                    "Informe técnico de estado del equipo", Color.WHITE);
            escribir(contenido, REGULAR, 9, MARGEN, ALTO_PAGINA - 82,
                    "Generado: " + fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                    new Color(220, 231, 243));
            y = ALTO_PAGINA - 119;
        }

        private void seccion(String titulo) throws IOException {
            asegurarEspacio(30);
            y -= 8;
            escribir(contenido, NEGRITA, 13, MARGEN, y, titulo, AZUL);
            y -= 7;
            contenido.setStrokingColor(new Color(195, 210, 225));
            contenido.moveTo(MARGEN, y);
            contenido.lineTo(ANCHO_PAGINA - MARGEN, y);
            contenido.stroke();
            y -= 15;
        }

        private void parrafo(String texto, PDFont fuente, float tamano, float interlineado,
                             Color color) throws IOException {
            for (String linea : envolver(limpiar(texto), fuente, tamano, ANCHO_CONTENIDO)) {
                asegurarEspacio(interlineado);
                escribir(contenido, fuente, tamano, MARGEN, y, linea, color);
                y -= interlineado;
            }
        }

        private void resultado(Diagnostico diagnostico) throws IOException {
            asegurarEspacio(42);
            String etiqueta = diagnostico.getCategoria() + "  |  " + diagnostico.getResultado();
            Color color = switch (diagnostico.getResultado()) {
                case "REVISAR" -> new Color(170, 55, 45);
                case "INFO" -> new Color(145, 100, 20);
                default -> new Color(35, 115, 75);
            };
            escribir(contenido, NEGRITA, 10, MARGEN, y, etiqueta, color);
            y -= 15;
            parrafo(diagnostico.getDetalle(), REGULAR, 9, 13, Color.BLACK);
            y -= 5;
        }

        private void problema(Problema problema) throws IOException {
            asegurarEspacio(48);
            Color color = "Alta".equalsIgnoreCase(problema.severidad())
                    ? new Color(170, 55, 45) : "OK".equalsIgnoreCase(problema.severidad())
                    ? new Color(35, 115, 75) : new Color(145, 100, 20);
            escribir(contenido, NEGRITA, 10, MARGEN, y,
                    problema.area() + "  |  " + problema.severidad(), color);
            y -= 15;
            parrafo(problema.problema(), REGULAR, 9, 13, Color.BLACK);
            parrafo("Recomendación: " + problema.recomendacion(), REGULAR, 9, 13, GRIS);
            y -= 6;
        }

        private void asegurarEspacio(float requerido) throws IOException {
            if (y - requerido < 55) {
                cerrarPagina();
                nuevaPagina();
            }
        }

        private void nuevaPagina() throws IOException {
            pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            contenido = new PDPageContentStream(documento, pagina);
            contenido.setNonStrokingColor(AZUL_CLARO);
            contenido.addRect(0, ALTO_PAGINA - 9, ANCHO_PAGINA, 9);
            contenido.fill();
            escribir(contenido, NEGRITA, 9, MARGEN, ALTO_PAGINA - 29,
                    "PC Doctor  |  Informe técnico", AZUL);
            y = ALTO_PAGINA - 50;
        }

        private void cerrarPagina() throws IOException {
            if (contenido != null) {
                contenido.close();
                contenido = null;
            }
        }

        private static List<String> envolver(String texto, PDFont fuente, float tamano,
                                              float anchoMaximo) throws IOException {
            List<String> lineas = new java.util.ArrayList<>();
            StringBuilder linea = new StringBuilder();
            for (String palabra : texto.split("\\s+")) {
                String candidato = linea.isEmpty() ? palabra : linea + " " + palabra;
                if (fuente.getStringWidth(candidato) / 1000 * tamano <= anchoMaximo) {
                    linea.setLength(0);
                    linea.append(candidato);
                } else {
                    if (!linea.isEmpty()) lineas.add(linea.toString());
                    linea.setLength(0);
                    linea.append(palabra);
                }
            }
            if (!linea.isEmpty()) lineas.add(linea.toString());
            return lineas;
        }
    }
}
