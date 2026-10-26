package runner;

import beast.base.core.BEASTVersion2;
import beast.base.inference.Logger;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.phylospec.tiling.TileCatalog;

/** Persistent journal for one managed run. It records observations, not scientific validity. */
final class RunRecord {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path directory;
    private ObjectNode data;

    private RunRecord(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    static RunRecord create(
            Path runDirectory, String source, Path sourceFile, Path sourceDirectory, TileCatalog<BEASTState> catalog)
            throws IOException {
        RunRecord record = new RunRecord(runDirectory);
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        Files.write(record.directory.resolve("model.phylospec"), bytes, StandardOpenOption.CREATE_NEW);
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
        script.put("inputBase", sourceDirectory.toAbsolutePath().normalize().toString());
        script.put("snapshot", "model.phylospec");
        script.put("sha256", HexFormat.of().formatHex(digest().digest(bytes)));
        ObjectNode runtime = initial.putObject("runtime");
        runtime.put("phylospecVersion", PhyloSpecCli.phyloSpecVersion());
        runtime.put("engine", "beast3");
        runtime.put("engineVersion", BEASTVersion2.INSTANCE.getVersionString());
        runtime.put("javaVersion", System.getProperty("java.version"));
        ArrayNode libraries = initial.putArray("libraries");
        for (var library : catalog.getLibraries()) {
            Class<?> implementation = library.getClass();
            ObjectNode entry = libraries.addObject();
            entry.put("id", library.getId());
            entry.put("class", implementation.getName());
            entry.put("version", implementation.getPackage().getImplementationVersion());
            var codeSource = implementation.getProtectionDomain().getCodeSource();
            entry.put(
                    "location",
                    codeSource == null ? null : codeSource.getLocation().toExternalForm());
        }
        ArrayNode components = initial.putArray("components");
        for (var component : catalog.getComponentLibraries()) {
            components.addObject().put("name", component.getName()).put("version", component.getVersion());
        }
        initial.putObject("seeds");
        initial.putArray("inputs");
        initial.put(
                "inputCoverage",
                "Only input references reported through BEASTState.resolvePath are recorded. Hashes are observed before the native reader and do not prove parsing succeeded. Input data are not copied; this is not a self-contained archive.");
        initial.putArray("warnings");
        initial.putArray("expectedOutputs");
        initial.putArray("generatedOutputs");
        record.save(initial);
        return record;
    }

    synchronized void warnings(List<PhyloSpecDiagnostic> warnings) throws IOException {
        ObjectNode next = editable();
        ArrayNode entries = next.putArray("warnings");
        warnings.forEach(warning -> entries.add(warning.format()));
        save(next);
    }

    synchronized void seed(String phase, Long requested, long observed, boolean generated) throws IOException {
        if (!phase.equals("modelBuilding") && !phase.equals("mcmc")) {
            throw new IllegalArgumentException("Unknown seed phase: " + phase);
        }
        ObjectNode next = editable();
        ((ObjectNode) next.get("seeds"))
                .putObject(phase)
                .put("requested", requested)
                .put("observed", observed)
                .put("generated", generated);
        save(next);
    }

    synchronized void input(String reference, Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (var input : data.withArray("inputs")) {
            if (input.path("reference").asText().equals(reference)
                    && input.path("path").asText().equals(absolute.toString())) return;
        }
        ObjectNode next = editable();
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
            entry.put("reason", message(failure));
        }
        save(next);
    }

    synchronized void configuration(BEASTState state, List<Logger> loggers, String stateFile) throws IOException {
        ObjectNode next = editable();
        ObjectNode settings = next.putObject("settings");
        settings.put("chainLength", state.chainLength);
        settings.put("defaultLogEvery", state.defaultLogEvery);
        settings.put("randomSeed", state.randomSeed);
        settings.put("outputPrefix", relative(state.outputPrefix));
        ArrayNode loggerSettings = settings.putArray("loggers");
        ArrayNode outputs = next.putArray("expectedOutputs");
        for (Logger logger : loggers) {
            String name = logger.fileNameInput.get();
            String kind = name == null || name.isEmpty()
                    ? "screen"
                    : logger.modeInput.get() == Logger.LOGMODE.tree ? "tree" : "log";
            ObjectNode entry = loggerSettings.addObject();
            entry.put("kind", kind);
            entry.put("logEvery", logger.everyInput.get());
            entry.put("path", kind.equals("screen") ? null : relative(name));
            if (!kind.equals("screen")) outputs.add(entry.deepCopy());
        }
        outputs.addObject().put("kind", "state").put("path", relative(stateFile));
        save(next);
    }

    synchronized void running() throws IOException {
        ObjectNode next = editable();
        next.put("status", "running");
        next.put("mcmcStartedAt", Instant.now().toString());
        save(next);
    }

    synchronized void succeeded() throws IOException {
        finish("succeeded", null);
    }

    synchronized void failed(Throwable failure) throws IOException {
        finish("failed", failure);
    }

    synchronized void cancelled(Throwable failure) throws IOException {
        finish("cancelled", failure);
    }

    synchronized void unconfirmed(String reason) throws IOException {
        if (isTerminal()) return;
        ObjectNode next = data.deepCopy();
        next.put("status", "unconfirmed");
        next.put("completionConfirmed", false);
        next.put("completionNote", reason);
        next.put("lastRecordedAt", Instant.now().toString());
        collectOutputs(next);
        save(next);
    }

    synchronized boolean isTerminal() {
        return data.path("completionConfirmed").asBoolean();
    }

    /** Actual logger and state paths, including user-specified filenames. */
    synchronized List<OutputFile> outputFiles() {
        var outputs = new java.util.ArrayList<OutputFile>();
        for (var entry : data.withArray("expectedOutputs")) {
            outputs.add(new OutputFile(
                    entry.path("kind").asText(),
                    directory.resolve(entry.path("path").asText())));
        }
        return List.copyOf(outputs);
    }

    record OutputFile(String kind, Path path) {}

    private void finish(String status, Throwable failure) throws IOException {
        if (isTerminal()) return;
        ObjectNode next = data.deepCopy();
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
            error.put("message", message(failure));
            if (failure instanceof PhyloSpecException diagnostic) {
                error.put("diagnostic", diagnostic.diagnostic().format());
            }
        }
        collectOutputs(next);
        save(next);
    }

    private ObjectNode editable() {
        if (isTerminal()) throw new IllegalStateException("The run record is already terminal.");
        return data.deepCopy();
    }

    private void collectOutputs(ObjectNode next) throws IOException {
        ArrayNode outputs = next.putArray("generatedOutputs");
        Path root = directory.resolve("output");
        if (!Files.exists(root)) return;
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList()) {
                outputs.addObject().put("path", relative(file.toString())).put("size", Files.size(file));
            }
        }
    }

    private String relative(String file) {
        return directory.relativize(Path.of(file).toAbsolutePath().normalize()).toString();
    }

    /** Publish a complete JSON document before accepting the corresponding in-memory transition. */
    private void save(ObjectNode next) throws IOException {
        Path temporary = Files.createTempFile(directory, ".run-", ".json.tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), next);
            try {
                Files.move(
                        temporary,
                        directory.resolve("run.json"),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, directory.resolve("run.json"), StandardCopyOption.REPLACE_EXISTING);
            }
            data = next;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String hash(Path file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream stream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible);
        }
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
