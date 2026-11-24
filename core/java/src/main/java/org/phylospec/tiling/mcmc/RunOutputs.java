package org.phylospec.tiling.mcmc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/// Defines where a managed run writes its files. Results are named after the run and live in the
/// working directory, next to a {@code <name>.run} folder holding the record of the run.
public final class RunOutputs {

    private static final List<String> RECORD_FILES =
            List.of("run.json", "console.log", "model.phylospec", "analysis.xml");

    private final Path workingDirectory;
    private final boolean overwrite;

    public RunOutputs(Path workingDirectory) {
        this(workingDirectory, false);
    }

    /** Creates the outputs of a run. With overwrite, existing output files are replaced. */
    public RunOutputs(Path workingDirectory, boolean overwrite) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.overwrite = overwrite;
    }

    /* default file names */

    public static String getLogFile(String prefix) {
        return prefix + ".log";
    }

    public static String getTreeFile(String prefix) {
        return prefix + ".trees";
    }

    public static String getTreeFile(String prefix, String treeName) {
        return prefix + "-" + treeName + ".trees";
    }

    public static String getStateFile(String prefix) {
        return prefix + ".state.xml";
    }

    public Path getRecordDirectory(String runName) {
        return this.workingDirectory.resolve(runName + ".run");
    }

    /** Makes sure that a run name is a plain name and not a path. */
    public static void validateRunName(String runName) {
        if (runName == null
                || runName.isBlank()
                || runName.equals(".")
                || runName.equals("..")
                || runName.contains("/")
                || runName.contains("\\")) {
            throw new IllegalArgumentException(
                    "The run name must be a plain name without a path. Change the working directory to write elsewhere.");
        }
    }

    /* run record */

    public static Path claimRecordDirectory(Path requested) throws IOException {
        return claimRecordDirectory(requested, false);
    }

    /**
     * Claims the record folder of a run. The folder of an earlier failed run that wrote no outputs is
     * replaced. Every other existing folder is refused, unless overwrite is set.
     */
    public static Path claimRecordDirectory(Path requested, boolean overwrite) throws IOException {
        Path directory = requested.toAbsolutePath().normalize();
        Files.createDirectories(directory.getParent());

        boolean isRecord = Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS);
        if (isRecord && (overwrite || isFailedRunWithoutOutputs(directory))) {
            deleteRecord(directory);
        }

        try {
            return Files.createDirectory(directory);
        } catch (FileAlreadyExistsException existing) {
            throw new IOException(
                    "A run with this name already exists: " + directory
                            + ". Use another run name, remove it or pass --overwrite.",
                    existing);
        }
    }

    private static boolean isFailedRunWithoutOutputs(Path directory) throws IOException {
        Path record = directory.resolve("run.json");
        if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS)) return false;

        try {
            JsonNode data = new ObjectMapper().readTree(record.toFile());
            return data.path("status").asText().equals("failed")
                    && data.path("generatedOutputs").isEmpty();
        } catch (JsonProcessingException malformed) {
            return false;
        }
    }

    /**
     * Deletes the record of an earlier run. The folder is left untouched if it holds anything else
     * than record files, and run.json and console.log are deleted last so that they are kept if the
     * cleanup fails.
     */
    private static void deleteRecord(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (!RECORD_FILES.contains(file.getFileName().toString())
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("The record of the earlier run contains an unexpected file: " + file
                            + ". Use another run name or remove " + directory + ".");
                }
            }
        }

        try {
            for (String file : RECORD_FILES.reversed()) {
                Files.deleteIfExists(directory.resolve(file));
            }
            Files.delete(directory);
        } catch (IOException error) {
            throw new IOException(
                    "Could not remove the record of the earlier run: " + directory
                            + ". Use another run name or remove it.",
                    error);
        }
    }

    /**
     * Returns how a record refers to a file: relative to the record folder if the file is next to
     * it or below, and as an absolute path otherwise.
     */
    public static String getRecordPath(Path recordDirectory, Path file) {
        Path directory = recordDirectory.toAbsolutePath().normalize();
        Path path = file.toAbsolutePath().normalize();
        return path.startsWith(directory.getParent())
                ? directory.relativize(path).toString()
                : path.toString();
    }

    /* output paths */

    /** Resolves an output name against the working directory. Absolute names are kept. */
    public String resolve(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Output path must not be blank; use screenLogger for console output.");
        }

        Path path = this.workingDirectory.resolve(name).normalize();
        if (path.toString().contains("$(tree)")) {
            throw new IllegalArgumentException(
                    "BEAST filename templates are not supported in managed output paths: " + name);
        }
        return path.toString();
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

    /**
     * Validates every destination before any file is opened. The state file is optional and also
     * reserves its temporary file. With overwrite, existing output files are deleted once all
     * destinations are valid.
     */
    public void prepare(List<String> loggerFiles, String stateFile) throws IOException {
        checkRuntimeOverrides();

        Set<Path> destinations = new LinkedHashSet<>();
        for (String file : loggerFiles) this.addDestination(destinations, file);
        if (stateFile != null) {
            this.addDestination(destinations, stateFile);
            this.addDestination(destinations, stateFile + ".new");
        }

        for (Path path : destinations) {
            for (Path other : destinations) {
                if (!path.equals(other) && path.startsWith(other)) {
                    throw new IllegalArgumentException("An output file is also used as a directory: " + other);
                }
            }
            this.checkParents(path.getParent());
            this.checkExistingOutput(path);
        }

        if (this.overwrite) {
            for (Path path : destinations) Files.deleteIfExists(path);
        }
        this.probeDestinations(destinations);
    }

    private void checkExistingOutput(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;

        if (!this.overwrite) {
            throw new IOException("Output already exists: " + path + ". Pass --overwrite to replace it.");
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Output is a directory; refusing to overwrite it: " + path);
        }
    }

    private void addDestination(Set<Path> destinations, String name) {
        Path path = Path.of(this.resolve(name));
        if (!destinations.add(path)) {
            throw new IllegalArgumentException("Multiple outputs use the same file: " + path);
        }
    }

    private void checkParents(Path parent) throws IOException {
        for (Path path = parent; path != null && path.startsWith(this.workingDirectory); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Output path must not pass through a symbolic link: " + path);
            }
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(path)) {
                throw new IOException("Output parent is not a directory: " + path);
            }
        }
    }

    /**
     * Creates and removes an empty file at every destination. This detects filesystem aliases too,
     * for example case-insensitive file names.
     */
    private void probeDestinations(Set<Path> destinations) throws IOException {
        List<Path> probes = new ArrayList<>();
        try {
            for (Path path : destinations) {
                Files.createDirectories(path.getParent());
                this.checkParents(path.getParent());
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
}
