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
    void encuentraArchivosConElMismoNombreYPeso(@TempDir Path carpeta)
            throws Exception {
        carpeta.resolve("uno").toFile().mkdir();
        carpeta.resolve("dos").toFile().mkdir();
        Path original = Files.writeString(carpeta.resolve("uno/documento.txt"),         "contenido1");
                Path duplicado = Files.writeString(carpeta.resolve("dos/documento.txt"), "contenido2");
        Files.writeString(carpeta.resolve("otro.txt"), "otro peso distinto");

        List<ArchivoDuplicadoService.GrupoDuplicado> grupos = service.buscar(carpeta);

        assertEquals(1, grupos.size());
        assertEquals(2, grupos.get(0).duplicados().size() + 1);
        assertEquals(List.of(original, duplicado).stream().filter(path -> !path.equals(grupos.get(0).original())).findFirst().orElseThrow(), grupos.get(0).duplicados().get(0));
    }
}
