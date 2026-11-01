package com.pcdoctor.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class YWCMComandoWindowsService {
    private static final Duration YWCMDEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public String YWCMejecutar(String... comando) {
        return YWCMejecutar(YWCMDEFAULT_TIMEOUT, comando);
    }

    public String YWCMejecutar(Duration tiempoMaximo, String... comando) {
        Process process = null;
        CompletableFuture<String> salida = null;
        try {
            process = new ProcessBuilder(comando).redirectErrorStream(true).start();
            Process procesoActivo = process;
            salida = CompletableFuture.supplyAsync(() -> YWCMleerSalida(procesoActivo));

            if (!process.waitFor(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return "El comando superó el tiempo máximo de " + tiempoMaximo.toSeconds() + " segundos.";
            }

            String texto = salida.get(5, TimeUnit.SECONDS).strip();
            if (process.exitValue() != 0) {
                String YWCMdetalle = texto.isBlank() ? "El comando no devolvió detalles." : texto;
                return "El comando terminó con código " + process.exitValue() + ":" +
                        System.lineSeparator() + YWCMdetalle;
            }
            return texto.isBlank() ? "Comando ejecutado correctamente sin salida." : texto;
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            return "La ejecución del comando fue interrumpida.";
        } catch (IOException e) {
            return "No se pudo iniciar el comando: " + e.getMessage();
        } catch (ExecutionException | TimeoutException e) {
            if (process != null) process.destroyForcibly();
            return "No se pudo leer la salida del comando: " +
                    (e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
        } finally {
            if (salida != null && !salida.isDone()) salida.cancel(true);
        }
    }

    private String YWCMleerSalida(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la salida del proceso.", e);
        }
    }
}
