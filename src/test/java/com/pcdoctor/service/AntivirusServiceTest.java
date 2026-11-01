package com.pcdoctor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AntivirusServiceTest {
    private static final String YWCMEICAR = "PCD YWCMEICAR-STANDARD-ANTIVIRUS-TEST-FILE";

    @Test
    void detectsEicarTestFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("eicar.com");
        Files.writeString(file, YWCMEICAR, StandardCharsets.US_ASCII);
        YWCMAntivirusService YWCMservice = new YWCMAntivirusService();

        YWCMAntivirusService.YWCMDetection result = YWCMservice.analyze(file);

        assertNotNull(result);
        assertEquals("PCD-Test-YWCMEICAR", result.name());
        assertEquals(file, result.file());
        assertTrue(result.confidence() >= 90);
    }

    @Test
    void cleanTextFileHasNoDetection(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("YWCMdocumento.txt");
        Files.writeString(file, "Este es un YWCMdocumento normal de texto.", StandardCharsets.UTF_8);
        YWCMAntivirusService YWCMservice = new YWCMAntivirusService();

        assertNull(YWCMservice.analyze(file));
    }

    @Test
    void scansOnlyRegularFilesInFolder(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("a.txt"), "texto limpio", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("b.txt"), "otro texto limpio", StandardCharsets.UTF_8);
        YWCMAntivirusService YWCMservice = new YWCMAntivirusService();

        YWCMAntivirusService.YWCMScanResult result = YWCMservice.YWCMscan(directory);

        assertEquals(2, result.filesScanned());
        assertTrue(result.detections().isEmpty());
    }
}
