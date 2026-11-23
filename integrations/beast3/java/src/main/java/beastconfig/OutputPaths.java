package beastconfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Path policy for a managed CLI run. Legacy Java callers do not use this policy. */
public final class OutputPaths {
    private final Path directory;

    public OutputPaths(Path workingDirectory) {
        this.directory = workingDirectory.toAbsolutePath().normalize();
    }

    /** Claims a new directory for the record of a run. */
    public static Path createRunDirectory(Path requested) throws IOException {
        Path runDirectory = requested.toAbsolutePath().normalize();
        Files.createDirectories(runDirectory.getParent());
        return Files.createDirectory(runDirectory);
    }

    /** Resolves an output name against the working directory. Absolute names are kept. */
    public String resolve(String name) {
        validateName(name);
        Path path = directory.resolve(name).normalize();
        if (path.toString().contains("$(tree)")) {
            throw new IllegalArgumentException(
                    "BEAST filename templates are not supported in managed output paths: " + name);
        }
        return path.toString();
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Output path must not be blank; use screenLogger for console output.");
        }
    }

    /** These native overrides would bypass the paths checked by the CLI. */
    public static void checkRuntimeOverrides() {
        for (String property : List.of("file.name.prefix", "state.file.name")) {
            if (System.getProperty(property) != null) {
                throw new IllegalArgumentException("Managed runs do not support -D" + property
                        + "; use --run-name and script output names instead.");
            }
        }
    }

    /** Validates every destination before MCMC opens any file, including the state temporary file. */
    public void prepare(List<String> loggerFiles, String stateFile) throws IOException {
        checkRuntimeOverrides();
        Set<Path> destinations = new LinkedHashSet<>();
        for (String file : loggerFiles) addDestination(destinations, file);
        addDestination(destinations, stateFile);
        addDestination(destinations, stateFile + ".new");
        for (Path path : destinations) {
            for (Path other : destinations) {
                if (!path.equals(other) && path.startsWith(other)) {
                    throw new IllegalArgumentException("An output file is also used as a directory: " + other);
                }
            }
            checkParents(path.getParent());
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Output already exists; refusing to overwrite: " + path);
            }
        }

        // CREATE_NEW detects filesystem aliases too (e.g. case-insensitive filenames).
        // Remove only our empty probes before BEAST's own file handling starts.
        List<Path> probes = new ArrayList<>();
        try {
            for (Path path : destinations) {
                Files.createDirectories(path.getParent());
                checkParents(path.getParent());
                probes.add(Files.createFile(path));
            }
        } finally {
            IOException cleanupFailure = null;
            for (Path probe : probes) {
                try {
                    Files.delete(probe);
                } catch (IOException failure) {
                    if (cleanupFailure == null) cleanupFailure = failure;
                    else cleanupFailure.addSuppressed(failure);
                }
            }
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }

    private void addDestination(Set<Path> destinations, String name) {
        Path path = Path.of(resolve(name));
        if (!destinations.add(path)) {
            throw new IllegalArgumentException("Multiple outputs use the same file: " + path);
        }
    }

    private void checkParents(Path parent) throws IOException {
        for (Path path = parent; path != null && path.startsWith(directory); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Output path must not pass through a symbolic link: " + path);
            }
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path)) {
                throw new IOException("Output parent is not a directory: " + path);
            }
        }
    }
}
