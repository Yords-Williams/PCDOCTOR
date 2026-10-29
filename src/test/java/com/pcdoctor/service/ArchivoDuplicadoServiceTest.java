package com.pcdoctor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchivoDuplicadoServiceTest {
    private final ArchivoDuplicadoService service = new ArchivoDuplicadoService();

    @Test
    void encuentraArchivosConElMismoContenidoPeroNoSoloElMismoTamano(@TempDir Path carpeta)
            throws Exception {
        Path original = Files.writeString(carpeta.resolve("a.txt"), "contenido idéntico");
        Path duplicado = Files.writeString(carpeta.resolve("b.txt"), "contenido idéntico");
        Files.writeString(carpeta.resolve("c.txt"), "contenido diferente");

        List<ArchivoDuplicadoService.GrupoDuplicado> grupos = service.buscar(carpeta);

        assertEquals(1, grupos.size());
        assertEquals(original, grupos.get(0).original());
        assertEquals(List.of(duplicado), grupos.get(0).duplicados());
    }
}
