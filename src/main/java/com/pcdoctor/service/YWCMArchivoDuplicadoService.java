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
public class YWCMArchivoDuplicadoService {
    public List<YWCMGrupoDuplicado> YWCMbuscar(Path directorio) throws IOException {
        // Cada grupo usa como identidad el nombre normalizado y el tamaño exacto del YWCMarchivo.
        if (!Files.isDirectory(directorio)) {
            throw new IOException("La carpeta seleccionada no existe o no es accesible: " + directorio);
        }
        Map<YWCMClaveDuplicado, List<Path>> archivosPorIdentidad = new LinkedHashMap<>();
        Files.walkFileTree(directorio, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path YWCMarchivo, BasicFileAttributes atributos) {
                if (atributos.isRegularFile()) {
                    String nombre = YWCMarchivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    archivosPorIdentidad.computeIfAbsent(new YWCMClaveDuplicado(nombre, atributos.size()), k -> new ArrayList<>())
                            .add(YWCMarchivo);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<YWCMGrupoDuplicado> YWCMresultado = new ArrayList<>();
        for (List<Path> archivos : archivosPorIdentidad.values()) {
            if (archivos.size() > 1) {
                // Se conserva el primero y el resto se presenta como duplicado eliminado posteriormente por el usuario.
                archivos.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
                YWCMresultado.add(new YWCMGrupoDuplicado(archivos.get(0), archivos.subList(1, archivos.size())));
            }
        }
        return List.copyOf(YWCMresultado);
    }

    private record YWCMClaveDuplicado(String nombre, long peso) { }

    public record YWCMGrupoDuplicado(Path YWCMoriginal, List<Path> duplicados) {
        public YWCMGrupoDuplicado { duplicados = List.copyOf(duplicados); }
    }
}
