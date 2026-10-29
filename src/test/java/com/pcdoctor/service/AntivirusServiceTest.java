package com.pcdoctor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntivirusServiceTest {
    @Test
    void parsesInfectedScanOutputAndExitCode(@TempDir Path directory) {
        Path scanner = directory.resolve("clamscan.exe");
        Path updater = directory.resolve("freshclam.exe");
        AntivirusService.Installation installation =
                new AntivirusService.Installation(directory, scanner, updater);
        Path infected = Path.of("C:\\sample\\test-eicar.com");
        String output = infected + ": Win.Test.EICAR_HDB-1 FOUND\n"
                + "----------- SCAN SUMMARY -----------\n"
                + "Scanned files: 4\n"
                + "Infected files: 1\n";

        AntivirusService.ScanResult result = AntivirusService.parseScanResult(
                Path.of("C:\\sample"), installation, 1, output);

        assertEquals(4, result.filesScanned());
        assertEquals(1, result.detections().size());
        assertEquals(infected, result.detections().get(0).file());
        assertEquals("Win.Test.EICAR_HDB-1", result.detections().get(0).name());
        assertTrue(result.output().contains("Infected files: 1"));
    }

    @Test
    void marksClamAvEngineErrorsAsIncompleteScan(@TempDir Path directory) {
        AntivirusService.Installation installation = new AntivirusService.Installation(
                directory, directory.resolve("clamscan.exe"), directory.resolve("freshclam.exe"));

        AntivirusService.ScanResult result = AntivirusService.parseScanResult(
                directory, installation, 2, "LibClamAV Error: database missing\n");

        assertTrue(result.completedWithErrors());
        assertEquals(0, result.detections().size());
    }

    @Test
    void parsesCleanScanOutputWithoutCallingItGuaranteedSafe(@TempDir Path directory) {
        AntivirusService.Installation installation = new AntivirusService.Installation(
                directory, directory.resolve("clamscan.exe"), directory.resolve("freshclam.exe"));

        AntivirusService.ScanResult result = AntivirusService.parseScanResult(
                directory, installation, 0,
                "----------- SCAN SUMMARY -----------\nScanned files: 12\nInfected files: 0\n");

        assertEquals(12, result.filesScanned());
        assertEquals(0, result.detections().size());
        assertEquals(0, result.exitCode());
        assertFalse(result.completedWithErrors());
    }

    @Test
    void locatesClamAvInConfiguredSearchDirectory(@TempDir Path directory) throws Exception {
        Files.createFile(directory.resolve("clamscan.exe"));
        Files.createFile(directory.resolve("freshclam.exe"));

        AntivirusService service = new AntivirusService(List.of(directory));

        assertTrue(service.findInstallation().isPresent());
        assertEquals(directory, service.findInstallation().orElseThrow().directory());
    }
}
