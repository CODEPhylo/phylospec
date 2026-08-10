package org.phylospec.beagle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Runs native BEAGLE verification in a disposable child JVM. */
public final class BeaglePreflightProcess {

    public record Request(
            BeagleRuntimeInstallation installation,
            Path javaExecutable,
            String classpath,
            List<String> jvmArguments,
            Map<String, String> environment,
            Duration timeout
    ) {
        private static final String JAVA_LIBRARY_PATH_PREFIX =
                "-Djava.library.path=";
        private static final String NATIVE_ACCESS_ARGUMENT =
                "--enable-native-access=ALL-UNNAMED";

        public Request {
            if (installation == null) {
                throw new IllegalArgumentException("installation must not be null.");
            }
            if (javaExecutable == null) {
                throw new IllegalArgumentException("javaExecutable must not be null.");
            }
            if (classpath == null || classpath.isBlank()) {
                throw new IllegalArgumentException("classpath must not be blank.");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive.");
            }

            javaExecutable = javaExecutable.toAbsolutePath().normalize();
            jvmArguments = List.copyOf(jvmArguments);
            environment = Map.copyOf(environment);
        }

        public static Request fromCurrentJvm(BeagleRuntimeInstallation installation) {
            return new Request(
                    installation,
                    currentJavaExecutable(),
                    System.getProperty("java.class.path"),
                    currentLibraryPathArgument(),
                    System.getenv(),
                    DEFAULT_TIMEOUT
            );
        }

        public List<String> resolvedJvmArguments() {
            List<String> resolved = new ArrayList<>();
            String configuredLibraryPath = "";

            for (String argument : jvmArguments) {
                if (argument.startsWith(JAVA_LIBRARY_PATH_PREFIX)) {
                    configuredLibraryPath = argument.substring(
                            JAVA_LIBRARY_PATH_PREFIX.length()
                    );
                } else if (!NATIVE_ACCESS_ARGUMENT.equals(argument)) {
                    resolved.add(argument);
                }
            }

            resolved.add(NATIVE_ACCESS_ARGUMENT);
            resolved.add(JAVA_LIBRARY_PATH_PREFIX
                    + mergeLibraryPath(
                            installation.libraryDirectory(),
                            configuredLibraryPath
                    ));
            return List.copyOf(resolved);
        }

        public Map<String, String> resolvedEnvironment() {
            java.util.LinkedHashMap<String, String> resolved =
                    new java.util.LinkedHashMap<>(environment);
            resolved.put(
                    BEAGLE_PLUGIN_PATH,
                    mergePathEntries(
                            installation.libraryDirectory(),
                            resolved.getOrDefault(BEAGLE_PLUGIN_PATH, "")
                    )
            );
            return Map.copyOf(resolved);
        }

        private static Path currentJavaExecutable() {
            String executable = System.getProperty("os.name", "")
                    .toLowerCase()
                    .contains("windows")
                    ? "java.exe"
                    : "java";
            return Path.of(System.getProperty("java.home"), "bin", executable);
        }

        private static List<String> currentLibraryPathArgument() {
            String libraryPath = System.getProperty("java.library.path", "");
            return libraryPath.isBlank()
                    ? List.of()
                    : List.of(JAVA_LIBRARY_PATH_PREFIX + libraryPath);
        }

        private static String mergeLibraryPath(Path libraryDirectory, String configured) {
            return mergePathEntries(libraryDirectory, configured);
        }

        private static String mergePathEntries(Path directory, String configured) {
            Set<String> entries = new LinkedHashSet<>();
            entries.add(directory.toString());
            for (String entry : configured.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    entries.add(entry);
                }
            }
            return String.join(File.pathSeparator, entries);
        }
    }

    public enum Status {
        AVAILABLE,
        UNAVAILABLE,
        CRASHED,
        TIMED_OUT,
        START_FAILED,
        INTERRUPTED,
        INVALID_OUTPUT
    }

    public record Result(
            Status status,
            Integer exitCode,
            BeagleRuntimeVerifier.Result verification,
            String standardOutput,
            String standardError,
            String problem
    ) {
        public Result {
            standardOutput = standardOutput == null ? "" : standardOutput;
            standardError = standardError == null ? "" : standardError;
        }

        public boolean available() {
            return status == Status.AVAILABLE;
        }
    }

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_CAPTURED_BYTES = 1_000_000;
    private static final String BEAGLE_PLUGIN_PATH = "BEAGLE_PLUGIN_PATH";

    private final String factoryClassName;

    public BeaglePreflightProcess() {
        this("beagle.BeagleFactory");
    }

    BeaglePreflightProcess(String factoryClassName) {
        this.factoryClassName = factoryClassName;
    }

    public Result run(BeagleRuntimeInstallation installation) {
        return run(Request.fromCurrentJvm(installation));
    }

    public Result run(Request request) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command(request));
            builder.environment().clear();
            builder.environment().putAll(request.resolvedEnvironment());
            process = builder.start();
        } catch (IOException exception) {
            return processFailure(Status.START_FAILED, exception);
        }

        CompletableFuture<String> standardOutput = capture(process.getInputStream());
        CompletableFuture<String> standardError = capture(process.getErrorStream());

        try {
            if (!process.waitFor(request.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                return new Result(
                        Status.TIMED_OUT,
                        null,
                        null,
                        standardOutput.join(),
                        standardError.join(),
                        "BEAGLE preflight exceeded " + request.timeout() + "."
                );
            }
        } catch (InterruptedException exception) {
            stop(process);
            Thread.currentThread().interrupt();
            return new Result(
                    Status.INTERRUPTED,
                    null,
                    null,
                    standardOutput.join(),
                    standardError.join(),
                    "Interrupted while waiting for BEAGLE preflight."
            );
        }

        int exitCode = process.exitValue();
        String output = standardOutput.join();
        String error = standardError.join();

        try {
            BeagleRuntimeVerifier.Result verification = parse(output);
            Status status = verification.available()
                    ? Status.AVAILABLE
                    : Status.UNAVAILABLE;
            if (verification.available() && exitCode != 0) {
                return new Result(
                        Status.INVALID_OUTPUT,
                        exitCode,
                        verification,
                        output,
                        error,
                        "Preflight reported AVAILABLE but exited with " + exitCode + "."
                );
            }
            return new Result(status, exitCode, verification, output, error, null);
        } catch (IllegalArgumentException exception) {
            Status status = exitCode == 0 ? Status.INVALID_OUTPUT : Status.CRASHED;
            return new Result(
                    status,
                    exitCode,
                    null,
                    output,
                    error,
                    exception.getMessage()
            );
        }
    }

    private List<String> command(Request request) {
        List<String> command = new ArrayList<>();
        command.add(request.javaExecutable().toString());
        command.addAll(request.resolvedJvmArguments());
        command.add("-cp");
        command.add(request.classpath());
        command.add(BeaglePreflightMain.class.getName());
        command.add(factoryClassName);
        return command;
    }

    private CompletableFuture<String> capture(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> read(stream));
    }

    private String read(InputStream stream) {
        try (stream; ByteArrayOutputStream captured = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                int remaining = MAX_CAPTURED_BYTES - total;
                if (remaining > 0) {
                    int copied = Math.min(read, remaining);
                    captured.write(buffer, 0, copied);
                    total += copied;
                }
            }
            return captured.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "Failed to capture process output: " + exception.getMessage();
        }
    }

    private void stop(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private BeagleRuntimeVerifier.Result parse(String output) {
        int begin = output.indexOf(BeaglePreflightMain.BEGIN_MARKER);
        int end = output.indexOf(BeaglePreflightMain.END_MARKER);
        if (begin < 0 || end <= begin) {
            throw new IllegalArgumentException(
                    "Preflight exited without a complete result; native code may have terminated the JVM."
            );
        }

        int contentStart = begin + BeaglePreflightMain.BEGIN_MARKER.length();
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(output.substring(contentStart, end)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not parse preflight output.", exception);
        }

        try {
            BeagleRuntimeVerifier.Status status = BeagleRuntimeVerifier.Status.valueOf(
                    required(properties, "status")
            );
            int resourceCount = Integer.parseInt(required(properties, "resource.count"));
            List<BeagleRuntimeVerifier.Resource> resources = new ArrayList<>();
            for (int index = 0; index < resourceCount; index++) {
                String prefix = "resource." + index + ".";
                resources.add(new BeagleRuntimeVerifier.Resource(
                        Integer.parseInt(required(properties, prefix + "number")),
                        required(properties, prefix + "name"),
                        required(properties, prefix + "description"),
                        Long.parseLong(required(properties, prefix + "flags"))
                ));
            }
            return new BeagleRuntimeVerifier.Result(
                    status,
                    emptyToNull(properties.getProperty("version")),
                    resources,
                    emptyToNull(properties.getProperty("problem"))
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Preflight returned an invalid result.", exception);
        }
    }

    private String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing preflight field: " + key);
        }
        return value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private Result processFailure(Status status, Exception exception) {
        return new Result(
                status,
                null,
                null,
                "",
                "",
                exception.getClass().getSimpleName() + ": " + exception.getMessage()
        );
    }
}
