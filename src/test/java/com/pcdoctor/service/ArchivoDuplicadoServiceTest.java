package com.pcdoctor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchivoDuplicadoServiceTest {
    private final YWCMArchivoDuplicadoService YWCMservice = new YWCMArchivoDuplicadoService();

    @Test
    void encuentraArchivosConElMismoNombreYPeso(@TempDir Path carpeta)
            throws Exception {
        carpeta.resolve("uno").toFile().mkdir();
        carpeta.resolve("dos").toFile().mkdir();
        Path YWCMoriginal = Files.writeString(carpeta.resolve("uno/YWCMdocumento.txt"),         "contenido1");
                Path duplicado = Files.writeString(carpeta.resolve("dos/YWCMdocumento.txt"), "contenido2");
        Files.writeString(carpeta.resolve("otro.txt"), "otro peso distinto");

        List<YWCMArchivoDuplicadoService.YWCMGrupoDuplicado> grupos = YWCMservice.YWCMbuscar(carpeta);

        assertEquals(1, grupos.size());
        assertEquals(2, grupos.get(0).duplicados().size() + 1);
        assertEquals(List.of(YWCMoriginal, duplicado).stream().filter(path -> !path.equals(grupos.get(0).YWCMoriginal())).findFirst().orElseThrow(), grupos.get(0).duplicados().get(0));
    }
}
