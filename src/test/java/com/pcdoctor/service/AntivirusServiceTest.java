package com.pcdoctor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AntivirusServiceTest {
    private static final String EICAR = "PCD EICAR-STANDARD-ANTIVIRUS-TEST-FILE";

    @Test
    void detectsEicarTestFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("eicar.com");
        Files.writeString(file, EICAR, StandardCharsets.US_ASCII);
        AntivirusService service = new AntivirusService();

        AntivirusService.Detection result = service.analyze(file);

        assertNotNull(result);
        assertEquals("PCD-Test-EICAR", result.name());
        assertEquals(file, result.file());
        assertTrue(result.confidence() >= 90);
    }

    @Test
    void cleanTextFileHasNoDetection(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("documento.txt");
        Files.writeString(file, "Este es un documento normal de texto.", StandardCharsets.UTF_8);
        AntivirusService service = new AntivirusService();

        assertNull(service.analyze(file));
    }

    @Test
    void scansOnlyRegularFilesInFolder(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("a.txt"), "texto limpio", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("b.txt"), "otro texto limpio", StandardCharsets.UTF_8);
        AntivirusService service = new AntivirusService();

        AntivirusService.ScanResult result = service.scan(directory);

        assertEquals(2, result.filesScanned());
        assertTrue(result.detections().isEmpty());
    }
}
