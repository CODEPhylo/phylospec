package org.phylospec.tiling.mcmc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The journal of one managed run, saved as run.json in the record folder of the run. It records
 * observations, not scientific validity. Every change is applied to a copy and only accepted once
 * the new run.json is saved, so the file and the journal in memory never disagree. Once the run
 * has a confirmed outcome, the journal can no longer be changed.
 */
public final class RunRecord {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path directory;
    private ObjectNode data;

    private RunRecord(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    /**
     * Starts the journal in an existing, empty record folder and saves the exact source of the
     * script next to it.
     */
    public static RunRecord create(
            Path directory,
            String source,
            Path sourceFile,
            Path inputBase,
            String engine,
            String engineVersion,
            String phyloSpecVersion)
            throws IOException {
        RunRecord record = new RunRecord(directory);
        Path snapshot =
                Files.writeString(record.directory.resolve("model.phylospec"), source, StandardOpenOption.CREATE_NEW);

        ObjectNode initial = JSON.createObjectNode();
        initial.put("schemaVersion", 1);
        initial.put("status", "preparing");
        initial.put("completionConfirmed", false);
        initial.put(
                "completionNote",
                "Completion is unconfirmed until a terminal status is recorded; a stopped process may leave this record incomplete.");
        initial.put("startedAt", Instant.now().toString());
        initial.putNull("endedAt");

        ObjectNode script = initial.putObject("source");
        script.put(
                "path",
                sourceFile == null
                        ? null
                        : sourceFile.toAbsolutePath().normalize().toString());
        script.put("inputBase", inputBase.toAbsolutePath().normalize().toString());
        script.put("snapshot", "model.phylospec");
        script.put("sha256", hash(snapshot));

        ObjectNode runtime = initial.putObject("runtime");
        runtime.put("phylospecVersion", phyloSpecVersion);
        runtime.put("engine", engine);
        runtime.put("engineVersion", engineVersion);
        runtime.put("javaVersion", System.getProperty("java.version"));

        initial.putObject("seeds");
        initial.putArray("inputs");
        initial.putArray("warnings");
        initial.putArray("expectedOutputs");
        initial.putArray("generatedOutputs");
        record.save(initial);
        return record;
    }

    public Path getDirectory() {
        return this.directory;
    }

    /* changes */

    /** A change to the journal. */
    public interface Change {
        void apply(ObjectNode data) throws IOException;
    }

    /**
     * Applies a change to a copy of the journal and saves it. Every change of the journal goes
     * through here, including the engine-specific ones.
     */
    public synchronized void update(Change change) throws IOException {
        if (this.isTerminal()) throw new IllegalStateException("The run record is already terminal.");

        ObjectNode next = this.data.deepCopy();
        change.apply(next);
        this.save(next);
    }

    public synchronized boolean isTerminal() {
        return this.data.path("completionConfirmed").asBoolean();
    }

    /** Saves a complete run.json before accepting the new journal in memory. */
    private void save(ObjectNode next) throws IOException {
        Path temporary = Files.createTempFile(this.directory, ".run-", ".json.tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), next);
            Path target = this.directory.resolve("run.json");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.data = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /* inputs, warnings and seeds */

    /**
     * Records an input file with its hash and size. A missing or unreadable input is recorded as
     * unavailable instead of failing. The reference is how the script names the file and may be
     * null.
     */
    public synchronized void addInput(String reference, Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (var input : this.data.withArray("inputs")) {
            if (Objects.equals(input.path("reference").textValue(), reference)
                    && input.path("path").asText().equals(absolute.toString())) return;
        }

        this.update(next -> {
            ObjectNode entry = next.withArray("inputs").addObject();
            entry.put("reference", reference);
            entry.put("path", absolute.toString());
            entry.put("fingerprintedAt", Instant.now().toString());
            try {
                if (!Files.isRegularFile(absolute)) throw new IOException("Input is missing or is not a regular file.");
                entry.put("sha256", hash(absolute));
                entry.put("size", Files.size(absolute));
                entry.put("status", "recorded");
            } catch (IOException | SecurityException failure) {
                entry.put("status", "unavailable");
                entry.putNull("sha256");
                entry.put("reason", getMessage(failure));
            }
        });
    }

    public void addWarning(String warning) throws IOException {
        this.update(next -> next.withArray("warnings").add(warning));
    }

    /** Records the seed requested for a phase of the run and the seed the engine actually reports. */
    public void recordSeed(String phase, Long requested, long observed, boolean generated) throws IOException {
        this.update(next -> ((ObjectNode) next.get("seeds"))
                .putObject(phase)
                .put("requested", requested)
                .put("observed", observed)
                .put("generated", generated));
    }

    /* settings and outputs */

    /** A logger of the run. The file is null for a screen logger. */
    public record LoggerSetting(String kind, long logEvery, String file) {}

    /**
     * Records the settings of the run and the files it is expected to write. The state file is
     * optional.
     */
    public void configure(
            long chainLength, Long randomSeed, String runName, List<LoggerSetting> loggers, String stateFile)
            throws IOException {
        this.update(next -> {
            ObjectNode settings = next.putObject("settings");
            settings.put("chainLength", chainLength);
            settings.put("randomSeed", randomSeed);
            settings.put("runName", runName);

            ArrayNode loggerSettings = settings.putArray("loggers");
            ArrayNode outputs = next.putArray("expectedOutputs");
            for (LoggerSetting logger : loggers) {
                ObjectNode entry = loggerSettings.addObject();
                entry.put("kind", logger.kind());
                entry.put("logEvery", logger.logEvery());
                entry.put("path", logger.file() == null ? null : this.getRecordPath(logger.file()));
                if (logger.file() != null) outputs.add(entry.deepCopy());
            }
            if (stateFile != null) {
                outputs.addObject().put("kind", "state").put("path", this.getRecordPath(stateFile));
            }
        });
    }

    private String getRecordPath(String file) {
        return OutputPaths.getRecordPath(this.directory, Path.of(file));
    }

    /** A file the run is expected to write. */
    public record OutputFile(String kind, Path path) {}

    /** The logger and state files the run is expected to write. */
    public synchronized List<OutputFile> getOutputFiles() {
        return this.data
                .withArray("expectedOutputs")
                .valueStream()
                .map(entry -> new OutputFile(
                        entry.path("kind").asText(),
                        this.resolve(entry.path("path").asText())))
                .toList();
    }

    private Path resolve(String recordPath) {
        return this.directory.resolve(recordPath).normalize();
    }

    /* outcome */

    public void markRunning() throws IOException {
        this.update(next ->
                next.put("status", "running").put("mcmcStartedAt", Instant.now().toString()));
    }

    public void markSucceeded() throws IOException {
        this.finish("succeeded", null, null);
    }

    /** Records a failed run. The diagnostic is an optional PhyloSpec explanation of the failure. */
    public void markFailed(Throwable failure, String diagnostic) throws IOException {
        this.finish("failed", failure, diagnostic);
    }

    /** Records a cancelled run. The diagnostic is an optional PhyloSpec explanation of the failure. */
    public void markCancelled(Throwable failure, String diagnostic) throws IOException {
        this.finish("cancelled", failure, diagnostic);
    }

    private synchronized void finish(String status, Throwable failure, String diagnostic) throws IOException {
        if (this.isTerminal()) return;

        this.update(next -> {
            next.put("status", status);
            next.put("completionConfirmed", true);
            next.put(
                    "completionNote",
                    status.equals("succeeded")
                            ? "The runner confirmed execution completed; this does not assess convergence or scientific validity."
                            : "The runner observed that execution did not complete successfully.");
            next.put("endedAt", Instant.now().toString());
            if (failure != null) {
                ObjectNode error = next.putObject("error");
                error.put("class", failure.getClass().getName());
                error.put("message", getMessage(failure));
                if (diagnostic != null) error.put("diagnostic", diagnostic);
            }
            this.collectOutputs(next);
        });
    }

    /**
     * Records that the process stops without a confirmed outcome, for example when the JVM shuts
     * down. A confirmed outcome is kept.
     */
    public synchronized void markUnconfirmed(String reason) throws IOException {
        if (this.isTerminal()) return;

        this.update(next -> {
            next.put("status", "unconfirmed");
            next.put("completionNote", reason);
            next.put("lastRecordedAt", Instant.now().toString());
            this.collectOutputs(next);
        });
    }

    private void collectOutputs(ObjectNode next) throws IOException {
        ArrayNode outputs = next.putArray("generatedOutputs");
        for (var entry : next.withArray("expectedOutputs")) {
            String path = entry.path("path").asText();
            Path file = this.resolve(path);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                outputs.addObject().put("path", path).put("size", Files.size(file));
            }
        }
    }

    /* helpers */

    /** Returns the SHA-256 hash of a file as lower-case hex. */
    public static String hash(Path file) throws IOException {
        try (var input = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(input.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible);
        }
    }

    private static String getMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
