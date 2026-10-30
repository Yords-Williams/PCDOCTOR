package com.pcdoctor.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArchivoDuplicadoService {
    public List<GrupoDuplicado> buscar(Path directorio) throws IOException {
        // Cada grupo usa como identidad el nombre normalizado y el tamaño exacto del archivo.
        if (!Files.isDirectory(directorio)) {
            throw new IOException("La carpeta seleccionada no existe o no es accesible: " + directorio);
        }
        Map<ClaveDuplicado, List<Path>> archivosPorIdentidad = new LinkedHashMap<>();
        Files.walkFileTree(directorio, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path archivo, BasicFileAttributes atributos) {
                if (atributos.isRegularFile()) {
                    String nombre = archivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    archivosPorIdentidad.computeIfAbsent(new ClaveDuplicado(nombre, atributos.size()), k -> new ArrayList<>())
                            .add(archivo);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<GrupoDuplicado> resultado = new ArrayList<>();
        for (List<Path> archivos : archivosPorIdentidad.values()) {
            if (archivos.size() > 1) {
                // Se conserva el primero y el resto se presenta como duplicado eliminado posteriormente por el usuario.
                archivos.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
                resultado.add(new GrupoDuplicado(archivos.get(0), archivos.subList(1, archivos.size())));
            }
        }
        return List.copyOf(resultado);
    }

    private record ClaveDuplicado(String nombre, long peso) { }

    public record GrupoDuplicado(Path original, List<Path> duplicados) {
        public GrupoDuplicado { duplicados = List.copyOf(duplicados); }
    }
}
