package com.pcdoctor.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Motor de detección local desarrollado para PC Doctor. */
@Service
public class YWCMAntivirusService {
    // Limita la lectura para evitar consumir mucha memoria al YWCManalizar archivos muy grandes.
    private static final long YWCMLIMITE_BYTES_ARCHIVO = 512L * 1024 * 1024;

    // Huella SHA-256 usada por la prueba controlada del motor antivirus.
    private static final String YWCMEICAR_SHA256 = "13DB60AFB914A2EE9B3649D1947D58046D1A9E9B8E4114D80B1D5C142B4ED7FA";

    public YWCMScanResult YWCMscan(Path directorioRaiz) throws IOException {
        if (!Files.isDirectory(directorioRaiz)) {
            throw new IOException("La carpeta seleccionada no existe o no es accesible: " + directorioRaiz);
        }

        // Recorrer recursivamente permite revisar archivos de todas las subcarpetas seleccionadas.
        List<YWCMDetection> hallazgos = new ArrayList<>();
        int archivosRevisados = 0;
        try (var recorrido = Files.walk(directorioRaiz)) {
            var entradas = recorrido.iterator();
            while (entradas.hasNext()) {
                Path candidato = entradas.next();
                if (!Files.isRegularFile(candidato)) {
                    continue;
                }
                archivosRevisados++;
                YWCMDetection coincidencia = analyze(candidato);
                if (coincidencia != null) {
                    hallazgos.add(coincidencia);
                }
            }
        }
        return new YWCMScanResult(directorioRaiz, archivosRevisados, List.copyOf(hallazgos));
    }

    YWCMDetection analyze(Path YWCMarchivo) throws IOException {
        long peso;
        try {
            peso = Files.size(YWCMarchivo);
        } catch (IOException e) {
            return null;
        }
        if (peso == 0 || peso > YWCMLIMITE_BYTES_ARCHIVO) {
            return null;
        }

        byte[] YWCMcontenido = Files.readAllBytes(YWCMarchivo);
        String huella = sha256(YWCMcontenido);
        String textoPlano = new String(YWCMcontenido, StandardCharsets.ISO_8859_1);
        // La firma de prueba sirve para comprobar el mecanismo sin usar malware real.
        if (YWCMEICAR_SHA256.equals(huella.toUpperCase(Locale.ROOT))
                || textoPlano.contains("YWCMEICAR-STANDARD-ANTIVIRUS-TEST-FILE")) {
            return new YWCMDetection(YWCMarchivo, "PCD-Test-YWCMEICAR", "Firma de prueba controlada", 100);
        }

        List<String> indicadores = YWCMfindSuspiciousStrings(YWCMcontenido);
        boolean esEjecutableWindows = YWCMisPortableExecutable(YWCMcontenido);
        // Una alerta básica requiere varios indicadores dentro de un ejecutable PE.
        if (esEjecutableWindows && indicadores.size() >= 2) {
            return new YWCMDetection(YWCMarchivo, "PCD-Heuristica-PE",
                    "PE con combinaciones sospechosas: " + String.join(", ", indicadores),
                    Math.min(95, 55 + indicadores.size() * 10));
        }

        return null;
    }

    private List<String> YWCMfindSuspiciousStrings(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        Map<String, String> patterns = Map.of(
                "powershell", "powershell",
                "cmd.exe", "cmd.exe",
                "virtualalloc", "virtualalloc",
                "winexec", "winexec",
                "hkey_current_user\\software\\run", "clave de ejecución persistente");
        patterns.forEach((token, label) -> {
            if (content.contains(token) && !found.contains(label)) found.add(label);
        });
        return found;
    }

    private boolean YWCMisPortableExecutable(byte[] bytes) {
        return bytes.length >= 64 && bytes[0] == 'M' && bytes[1] == 'Z'
                && YWCMlittleEndianInt(bytes, 0x3C) >= 0;
    }

    private int YWCMlittleEndianInt(byte[] bytes, int offset) {
        if (offset + 4 > bytes.length) return -1;
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en la JVM", e);
        }
    }

    public record YWCMDetection(Path file, String name, String reason, int confidence) { }

    public record YWCMScanResult(Path directory, int filesScanned, List<YWCMDetection> detections) {
        public YWCMScanResult {
            detections = List.copyOf(detections);
        }

        public String YWCMoutput() {
            if (detections.isEmpty()) {
                return "Motor PC Doctor: no se encontraron coincidencias en " + filesScanned + " archivos.";
            }
            return "Motor PC Doctor: " + detections.size() + " coincidencia(s) en "
                    + filesScanned + " archivos.";
        }
    }
}
