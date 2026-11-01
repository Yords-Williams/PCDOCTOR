package com.pcdoctor.service;

import com.pcdoctor.model.YWCMDiagnostico;
import com.pcdoctor.model.YWCMProblema;
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
public class YWCMReportePdfService {
    private static final float YWCMMARGEN = 48;
    private static final float YWCMANCHO_PAGINA = PDRectangle.A4.getWidth();
    private static final float YWCMALTO_PAGINA = PDRectangle.A4.getHeight();
    private static final float YWCMANCHO_CONTENIDO = YWCMANCHO_PAGINA - YWCMMARGEN * 2;
    private static final PDFont YWCMREGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont YWCMNEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color YWCMAZUL = new Color(25, 55, 90);
    private static final Color YWCMAZUL_CLARO = new Color(232, 240, 248);
    private static final Color YWCMGRIS = new Color(90, 100, 112);

    private final YWCMDiagnosticoService YWCMdiagnosticoService;

    public YWCMReportePdfService(YWCMDiagnosticoService YWCMdiagnosticoService) {
        this.YWCMdiagnosticoService = YWCMdiagnosticoService;
    }

    public Path YWCMgenerar() throws Exception {
        Path directorio = Path.of(System.getProperty("user.home"), "Downloads");
        return YWCMgenerar(directorio);
    }

    public Path YWCMgenerar(Path directorio) throws Exception {
        // El diagnóstico se realiza antes de construir el YWCMdocumento para usar datos consistentes.
        YWCMAnalisisCompleto analisis = YWCMdiagnosticoService.YWCManalizarYGuardar();
        Files.createDirectories(directorio);
        LocalDateTime ahora = LocalDateTime.now();
        String marcaTiempo = ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path YWCMarchivo = directorio.resolve("reporte-" + marcaTiempo + ".pdf");

        try (PDDocument YWCMdocumento = new PDDocument()) {
            // YWCMLayout centraliza saltos de página, márgenes y ajuste de texto largo.
            YWCMLayout layout = new YWCMLayout(YWCMdocumento);
            layout.YWCMtitulo(ahora);

            layout.YWCMseccion("Información del equipo");
            layout.YWCMparrafo(analisis.sistema(), YWCMREGULAR, 10, 15, YWCMGRIS);

            layout.YWCMseccion("Unidades de almacenamiento");
            for (String unidad : analisis.almacenamiento().split("\\R")) {
                layout.YWCMparrafo(unidad, YWCMREGULAR, 10, 15, Color.BLACK);
            }

            layout.YWCMseccion("Resultados del análisis");
            for (YWCMDiagnostico diagnostico : analisis.diagnosticos()) {
                layout.YWCMresultado(diagnostico);
            }

            layout.YWCMseccion("Alertas y recomendaciones");
            for (YWCMProblema YWCMproblema : analisis.problemas()) {
                layout.YWCMproblema(YWCMproblema);
            }

            layout.YWCMcerrarPagina();
            YWCMagregarPiesDePagina(YWCMdocumento);
            YWCMdocumento.getDocumentInformation().setTitle("PC Doctor - Informe técnico");
            YWCMdocumento.getDocumentInformation().setSubject("Análisis de estado del equipo");
            YWCMdocumento.getDocumentInformation().setCreationDate(
                    java.util.GregorianCalendar.from(ahora.atZone(java.time.ZoneId.systemDefault())));
            YWCMdocumento.save(YWCMarchivo.toFile());
        }
        return YWCMarchivo;
    }

    private void YWCMagregarPiesDePagina(PDDocument YWCMdocumento) throws IOException {
        int totalPaginas = YWCMdocumento.getNumberOfPages();
        for (int i = 0; i < totalPaginas; i++) {
            PDPage YWCMpagina = YWCMdocumento.getPage(i);
            try (PDPageContentStream YWCMcontenido =
                         new PDPageContentStream(YWCMdocumento, YWCMpagina, AppendMode.APPEND, true, true)) {
                YWCMcontenido.setStrokingColor(new Color(210, 218, 226));
                YWCMcontenido.moveTo(YWCMMARGEN, 37);
                YWCMcontenido.lineTo(YWCMANCHO_PAGINA - YWCMMARGEN, 37);
                YWCMcontenido.stroke();
                YWCMescribir(YWCMcontenido, YWCMREGULAR, 8, YWCMMARGEN, 23,
                        "PC Doctor  |  Informe técnico", YWCMGRIS);
                String paginaTexto = "Página " + (i + 1) + " de " + totalPaginas;
                float ancho = YWCMREGULAR.getStringWidth(paginaTexto) / 1000 * 8;
                YWCMescribir(YWCMcontenido, YWCMREGULAR, 8, YWCMANCHO_PAGINA - YWCMMARGEN - ancho, 23,
                        paginaTexto, YWCMGRIS);
            }
        }
    }

    private static void YWCMescribir(PDPageContentStream YWCMcontenido, PDFont fuente, float tamano,
                                 float x, float y, String texto, Color color) throws IOException {
        YWCMcontenido.beginText();
        YWCMcontenido.setFont(fuente, tamano);
        YWCMcontenido.setNonStrokingColor(color);
        YWCMcontenido.newLineAtOffset(x, y);
        YWCMcontenido.showText(YWCMlimpiar(texto));
        YWCMcontenido.endText();
    }

    private static String YWCMlimpiar(String texto) {
        String sinControles = texto.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return new String(sinControles.getBytes(Charset.forName("windows-1252")),
                Charset.forName("windows-1252"));
    }

    private static final class YWCMLayout {
        private final PDDocument YWCMdocumento;
        private PDPage YWCMpagina;
        private PDPageContentStream YWCMcontenido;
        private float y;

        private YWCMLayout(PDDocument YWCMdocumento) throws IOException {
            this.YWCMdocumento = YWCMdocumento;
            YWCMnuevaPagina();
        }

        private void YWCMtitulo(LocalDateTime YWCMfecha) throws IOException {
            YWCMcontenido.setNonStrokingColor(YWCMAZUL);
            YWCMcontenido.addRect(0, YWCMALTO_PAGINA - 96, YWCMANCHO_PAGINA, 96);
            YWCMcontenido.fill();
            YWCMescribir(YWCMcontenido, YWCMNEGRITA, 22, YWCMMARGEN, YWCMALTO_PAGINA - 46,
                    "PC Doctor", Color.WHITE);
            YWCMescribir(YWCMcontenido, YWCMREGULAR, 11, YWCMMARGEN, YWCMALTO_PAGINA - 66,
                    "Informe técnico de estado del equipo", Color.WHITE);
            YWCMescribir(YWCMcontenido, YWCMREGULAR, 9, YWCMMARGEN, YWCMALTO_PAGINA - 82,
                    "Generado: " + YWCMfecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                    new Color(220, 231, 243));
            y = YWCMALTO_PAGINA - 119;
        }

        private void YWCMseccion(String YWCMtitulo) throws IOException {
            YWCMasegurarEspacio(30);
            y -= 8;
            YWCMescribir(YWCMcontenido, YWCMNEGRITA, 13, YWCMMARGEN, y, YWCMtitulo, YWCMAZUL);
            y -= 7;
            YWCMcontenido.setStrokingColor(new Color(195, 210, 225));
            YWCMcontenido.moveTo(YWCMMARGEN, y);
            YWCMcontenido.lineTo(YWCMANCHO_PAGINA - YWCMMARGEN, y);
            YWCMcontenido.stroke();
            y -= 15;
        }

        private void YWCMparrafo(String texto, PDFont fuente, float tamano, float interlineado,
                             Color color) throws IOException {
            for (String linea : YWCMenvolver(YWCMlimpiar(texto), fuente, tamano, YWCMANCHO_CONTENIDO)) {
                YWCMasegurarEspacio(interlineado);
                YWCMescribir(YWCMcontenido, fuente, tamano, YWCMMARGEN, y, linea, color);
                y -= interlineado;
            }
        }

        private void YWCMresultado(YWCMDiagnostico diagnostico) throws IOException {
            YWCMasegurarEspacio(42);
            String etiqueta = diagnostico.YWCMgetCategoria() + "  |  " + diagnostico.YWCMgetResultado();
            Color color = switch (diagnostico.YWCMgetResultado()) {
                case "REVISAR" -> new Color(170, 55, 45);
                case "INFO" -> new Color(145, 100, 20);
                default -> new Color(35, 115, 75);
            };
            YWCMescribir(YWCMcontenido, YWCMNEGRITA, 10, YWCMMARGEN, y, etiqueta, color);
            y -= 15;
            YWCMparrafo(diagnostico.YWCMgetDetalle(), YWCMREGULAR, 9, 13, Color.BLACK);
            y -= 5;
        }

        private void YWCMproblema(YWCMProblema YWCMproblema) throws IOException {
            YWCMasegurarEspacio(48);
            Color color = "Alta".equalsIgnoreCase(YWCMproblema.severidad())
                    ? new Color(170, 55, 45) : "OK".equalsIgnoreCase(YWCMproblema.severidad())
                    ? new Color(35, 115, 75) : new Color(145, 100, 20);
            YWCMescribir(YWCMcontenido, YWCMNEGRITA, 10, YWCMMARGEN, y,
                    YWCMproblema.area() + "  |  " + YWCMproblema.severidad(), color);
            y -= 15;
            YWCMparrafo(YWCMproblema.YWCMproblema(), YWCMREGULAR, 9, 13, Color.BLACK);
            YWCMparrafo("Recomendación: " + YWCMproblema.recomendacion(), YWCMREGULAR, 9, 13, YWCMGRIS);
            y -= 6;
        }

        private void YWCMasegurarEspacio(float requerido) throws IOException {
            if (y - requerido < 55) {
                YWCMcerrarPagina();
                YWCMnuevaPagina();
            }
        }

        private void YWCMnuevaPagina() throws IOException {
            YWCMpagina = new PDPage(PDRectangle.A4);
            YWCMdocumento.addPage(YWCMpagina);
            YWCMcontenido = new PDPageContentStream(YWCMdocumento, YWCMpagina);
            YWCMcontenido.setNonStrokingColor(YWCMAZUL_CLARO);
            YWCMcontenido.addRect(0, YWCMALTO_PAGINA - 9, YWCMANCHO_PAGINA, 9);
            YWCMcontenido.fill();
            YWCMescribir(YWCMcontenido, YWCMNEGRITA, 9, YWCMMARGEN, YWCMALTO_PAGINA - 29,
                    "PC Doctor  |  Informe técnico", YWCMAZUL);
            y = YWCMALTO_PAGINA - 50;
        }

        private void YWCMcerrarPagina() throws IOException {
            if (YWCMcontenido != null) {
                YWCMcontenido.close();
                YWCMcontenido = null;
            }
        }

        private static List<String> YWCMenvolver(String texto, PDFont fuente, float tamano,
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
