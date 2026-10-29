package com.pcdoctor.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.WINDOWS)
class ComandoWindowsServiceTest {
    private final ComandoWindowsService comandos = new ComandoWindowsService();

    @Test
    void leeSalidaGrandeMientrasElProcesoSigueActivo() {
        String salida = comandos.ejecutar(Duration.ofSeconds(15), "cmd", "/c",
                "for /L %i in (1,1,5000) do @echo salida-%i");

        assertTrue(salida.contains("salida-1"));
        assertTrue(salida.contains("salida-5000"));
    }

    @Test
    void informaCodigoDeSalidaNoExitoso() {
        String salida = comandos.ejecutar(Duration.ofSeconds(5), "cmd", "/c", "exit 7");

        assertTrue(salida.startsWith("El comando terminó con código 7:"));
    }
}
