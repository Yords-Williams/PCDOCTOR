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
public class ComandoWindowsService {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public String ejecutar(String... comando) {
        return ejecutar(DEFAULT_TIMEOUT, comando);
    }

    public String ejecutar(Duration tiempoMaximo, String... comando) {
        Process process = null;
        CompletableFuture<String> salida = null;
        try {
            process = new ProcessBuilder(comando).redirectErrorStream(true).start();
            Process procesoActivo = process;
            salida = CompletableFuture.supplyAsync(() -> leerSalida(procesoActivo));

            if (!process.waitFor(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return "El comando superó el tiempo máximo de " + tiempoMaximo.toSeconds() + " segundos.";
            }

            String texto = salida.get(5, TimeUnit.SECONDS).strip();
            if (process.exitValue() != 0) {
                String detalle = texto.isBlank() ? "El comando no devolvió detalles." : texto;
                return "El comando terminó con código " + process.exitValue() + ":" +
                        System.lineSeparator() + detalle;
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

    private String leerSalida(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la salida del proceso.", e);
        }
    }
}
