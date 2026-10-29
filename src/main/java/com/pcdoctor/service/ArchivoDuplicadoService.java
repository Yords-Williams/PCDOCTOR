package com.pcdoctor.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ArchivoDuplicadoService {
    public List<GrupoDuplicado> buscar(Path directorio) throws IOException {
        if (!Files.isDirectory(directorio)) {
            throw new IOException("La carpeta seleccionada no existe o no es accesible: " + directorio);
        }

        Map<Long, List<Path>> porTamano = new TreeMap<>();
        Files.walkFileTree(directorio, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) {
                if (atributos.isRegularFile()) {
                    porTamano.computeIfAbsent(atributos.size(), ignorado -> new ArrayList<>())
                            .add(archivo);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        Map<String, List<Path>> porContenido = new LinkedHashMap<>();
        for (List<Path> archivosMismoTamano : porTamano.values()) {
            if (archivosMismoTamano.size() < 2) {
                continue;
            }
            archivosMismoTamano.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
            for (Path archivo : archivosMismoTamano) {
                porContenido.computeIfAbsent(hash(archivo), ignorado -> new ArrayList<>()).add(archivo);
            }
        }

        List<GrupoDuplicado> grupos = new ArrayList<>();
        for (Map.Entry<String, List<Path>> entrada : porContenido.entrySet()) {
            List<Path> archivos = entrada.getValue();
            if (archivos.size() > 1) {
                grupos.add(new GrupoDuplicado(archivos.get(0), archivos.subList(1, archivos.size())));
            }
        }
        return List.copyOf(grupos);
    }

    private String hash(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var entrada = Files.newInputStream(archivo)) {
                byte[] buffer = new byte[8192];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1) {
                    digest.update(buffer, 0, leidos);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no dispone del algoritmo SHA-256.", e);
        }
    }

    public record GrupoDuplicado(Path original, List<Path> duplicados) {
        public GrupoDuplicado {
            duplicados = List.copyOf(duplicados);
        }
    }
}
