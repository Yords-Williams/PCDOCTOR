package com.pcdoctor.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AntivirusService {
    private static final Duration SCAN_TIMEOUT = Duration.ofHours(2);
    private static final Duration UPDATE_TIMEOUT = Duration.ofMinutes(30);
    private static final Pattern DETECTION_LINE =
            Pattern.compile("^(.*):\\s+(.+)\\s+FOUND\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCANNED_FILES =
            Pattern.compile("^Scanned files:\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private final List<Path> searchDirectories;

    public AntivirusService() {
        this(defaultSearchDirectories());
    }

    AntivirusService(List<Path> searchDirectories) {
        this.searchDirectories = List.copyOf(searchDirectories);
    }

    public Optional<Installation> findInstallation() {
        for (Path directory : searchDirectories) {
            Path scanner = directory.resolve("clamscan.exe");
            Path updater = directory.resolve("freshclam.exe");
            if (Files.isRegularFile(scanner) && Files.isRegularFile(updater)) {
                return Optional.of(new Installation(directory, scanner, updater));
            }
        }
        return Optional.empty();
    }

    public UpdateResult updateSignatures() throws IOException, InterruptedException {
        Installation installation = findInstallation().orElseThrow(() ->
                new IOException("ClamAV no está instalado o no se encontró. "
                        + "Instálalo y configura CLAMAV_HOME o añade su carpeta al PATH."));
        List<String> updateArguments = new ArrayList<>();
        Optional<Path> bundledDatabase = bundledDatabaseDirectory(installation);
        if (bundledDatabase.isPresent()) {
            Path database = bundledDatabase.get();
            updateArguments.add("--config-file=" + writeFreshclamConfig(installation, database));
            updateArguments.add("--datadir=" + database);
        }
        CommandResult result = execute(installation.freshclam(), updateArguments, UPDATE_TIMEOUT,
                installation.directory());
        if (result.exitCode() != 0) {
            throw new IOException("freshclam terminó con código " + result.exitCode()
                    + System.lineSeparator() + result.output());
        }
        return new UpdateResult(installation, result.output());
    }

    public ScanResult scan(Path directory) throws IOException, InterruptedException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("La carpeta seleccionada no existe o no es accesible: " + directory);
        }
        Installation installation = findInstallation().orElseThrow(() ->
                new IOException("ClamAV no está instalado o no se encontró. "
                        + "Instálalo y configura CLAMAV_HOME o añade su carpeta al PATH."));
        List<String> arguments = new ArrayList<>(List.of("--recursive", "--infected"));
        bundledDatabaseDirectory(installation).ifPresent(database ->
                arguments.addAll(List.of("--database=" + database)));
        arguments.add(directory.toAbsolutePath().toString());
        CommandResult result = execute(installation.clamScan(), arguments, SCAN_TIMEOUT,
                installation.directory());
        if (result.exitCode() != 0 && result.exitCode() != 1 && result.exitCode() != 2) {
            throw new IOException("clamscan terminó con código inesperado " + result.exitCode()
                    + System.lineSeparator() + result.output());
        }
        return parseScanResult(directory, installation, result.exitCode(), result.output());
    }

    private CommandResult execute(Path executable, List<String> arguments, Duration timeout)
            throws IOException, InterruptedException {
        return execute(executable, arguments, timeout, executable.toAbsolutePath().getParent());
    }

    private CommandResult execute(Path executable, List<String> arguments, Duration timeout,
                                  Path workingDirectory)
            throws IOException, InterruptedException {
        Path outputFile = Files.createTempFile("pcdoctor-clamav-", ".log");
        Process process = null;
        try {
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.addAll(arguments);
            process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stopProcess(process);
                throw new IOException(executable.getFileName() + " superó el tiempo máximo de "
                        + timeout.toMinutes() + " minutos.");
            }
            return new CommandResult(process.exitValue(),
                    Files.readString(outputFile, Charset.defaultCharset()));
        } catch (InterruptedException e) {
            if (process != null) {
                stopProcess(process);
            }
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private void stopProcess(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    static ScanResult parseScanResult(Path directory, Installation installation,
                                      int exitCode, String output) {
        List<Detection> detections = new ArrayList<>();
        int scannedFiles = 0;
        for (String line : output.split("\\R")) {
            Matcher detection = DETECTION_LINE.matcher(line.trim());
            if (detection.matches()) {
                detections.add(new Detection(Path.of(detection.group(1)),
                        detection.group(2).trim()));
                continue;
            }
            Matcher scanned = SCANNED_FILES.matcher(line.trim());
            if (scanned.matches()) {
                scannedFiles = Integer.parseInt(scanned.group(1));
            }
        }
        return new ScanResult(directory, installation, scannedFiles, detections,
                exitCode, output);
    }

    private static List<Path> defaultSearchDirectories() {
        List<Path> directories = new ArrayList<>();
        addBundledDirectory(directories, Path.of(System.getProperty("java.home")).getParent()
                .resolve("app").resolve("clamav"));
        addBundledDirectory(directories, Path.of(System.getProperty("user.dir")).resolve("clamav"));
        String clamAvHome = System.getenv("CLAMAV_HOME");
        if (clamAvHome != null && !clamAvHome.isBlank()) {
            directories.add(Path.of(clamAvHome.trim()));
        }

        addEnvironmentDirectory(directories, "ProgramFiles", "ClamAV");
        addEnvironmentDirectory(directories, "ProgramFiles(x86)", "ClamAV");
        directories.add(Path.of("C:\\ClamAV"));

        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(Pattern.quote(System.getProperty("path.separator")))) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        directories.add(Path.of(trimmed));
                    } catch (InvalidPathException ignored) {
                        // Invalid PATH entries cannot contain the ClamAV executables.
                    }
                }
            }
        }
        return directories.stream().distinct().toList();
    }

    private static void addBundledDirectory(List<Path> directories, Path directory) {
        if (Files.isRegularFile(directory.resolve("clamscan.exe"))) {
            directories.add(directory);
        }
    }

    private Optional<Path> bundledDatabaseDirectory(Installation installation) {
        if (!Files.isRegularFile(installation.directory().resolve(".pcdoctor-bundled"))) {
            return Optional.empty();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        Path database = localAppData == null || localAppData.isBlank()
                ? installation.directory().resolve("database")
                : Path.of(localAppData).resolve("PCDoctor").resolve("clamav-database");
        try {
            Files.createDirectories(database);
            Path bundledDatabase = installation.directory().resolve("database");
            if (Files.isDirectory(bundledDatabase)) {
                try (java.util.stream.Stream<Path> files = Files.list(bundledDatabase)) {
                    for (Path source : files.filter(Files::isRegularFile).toList()) {
                        Path destination = database.resolve(source.getFileName());
                        if (!Files.exists(destination)) {
                            Files.copy(source, destination);
                        }
                    }
                }
            }
            return Optional.of(database.toAbsolutePath());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String writeFreshclamConfig(Installation installation, Path database) throws IOException {
        Path config = database.resolve("freshclam.conf");
        Path certificates = installation.directory().resolve("certs");
        String content = "DatabaseDirectory \"" + database.toAbsolutePath() + "\"\n"
                + "CVDCertsDirectory \"" + certificates.toAbsolutePath() + "\"\n"
                + "DatabaseMirror database.clamav.net\n"
                + "UpdateLogFile \"" + database.resolve("freshclam.log") + "\"\n";
        Files.writeString(config, content, Charset.defaultCharset(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return config.toAbsolutePath().toString();
    }

    private static void addEnvironmentDirectory(List<Path> directories, String variable,
                                               String child) {
        String value = System.getenv(variable);
        if (value != null && !value.isBlank()) {
            directories.add(Path.of(value).resolve(child));
        }
    }

    public record Installation(Path directory, Path clamScan, Path freshclam) {
    }

    public record Detection(Path file, String name) {
    }

    public record ScanResult(Path directory, Installation installation, int filesScanned,
                             List<Detection> detections, int exitCode, String output) {
        public ScanResult {
            detections = List.copyOf(detections);
        }

        public boolean completedWithErrors() {
            return exitCode == 2;
        }
    }

    public record UpdateResult(Installation installation, String output) {
        public boolean hasOutput() {
            return !output.isBlank();
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
