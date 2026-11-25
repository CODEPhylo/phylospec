import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dr.app.beast.BeastVersion;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import org.phylospec.tiling.mcmc.OutputPaths;
import tiling.BeastXState;

/** Journal for the isolated BEAST X process. All result paths are relative to the managed run. */
final class BeastXRunRecord {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path directory;
    private final ObjectNode data = JSON.createObjectNode();

    private BeastXRunRecord(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    static BeastXRunRecord create(Path directory, Path source) throws IOException {
        BeastXRunRecord record = new BeastXRunRecord(directory);
        Path original = source.toAbsolutePath().normalize();
        Path snapshot = record.directory.resolve("model.phylospec");
        Files.copy(original, snapshot);
        ObjectNode data = record.data;
        data.put("schemaVersion", 1).put("status", "preparing").put("completionConfirmed", false);
        data.put("startedAt", Instant.now().toString()).putNull("endedAt");
        data.putObject("source").put("path", original.toString()).put("inputBase", original.getParent().toString())
                .put("snapshot", "model.phylospec").put("sha256", sha256(snapshot));
        data.putObject("runtime").put("engine", "beastx").put("engineVersion", BeastVersion.INSTANCE.getVersion())
                .put("phylospecVersion", BeastXState.class.getPackage().getImplementationVersion())
                .put("javaVersion", System.getProperty("java.version"));
        data.putObject("seeds");
        data.putArray("warnings");
        data.putArray("inputs");
        data.putArray("expectedOutputs");
        data.putArray("generatedOutputs");
        record.save();
        return record;
    }

    String sourceText() throws IOException {
        return Files.readString(directory.resolve("model.phylospec"));
    }

    void input(Path file) {
        Path input = file.toAbsolutePath().normalize();
        ArrayNode inputs = data.withArray("inputs");
        for (var entry : inputs) {
            if (entry.path("path").asText().equals(input.toString())) return;
        }
        ObjectNode entry = inputs.addObject().put("path", input.toString()).put("fingerprintedAt", Instant.now().toString());
        try {
            entry.put("sha256", sha256(input)).put("size", Files.size(input)).put("status", "recorded");
        } catch (IOException failure) {
            entry.put("status", "unavailable").put("reason", failure.toString());
        }
    }

    void warning(String message) {
        data.withArray("warnings").add(message);
    }

    void seed(String phase, Long requested, long observed) {
        ObjectNode seeds = (ObjectNode) data.get("seeds");
        seeds.putObject(phase).put("requested", requested).put("observed", observed)
                .put("generated", requested == null);
        try {
            save();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    void configure(BeastXState state, Path publishedXml, long effectiveSeed, boolean overwrite) throws IOException {
        data.putObject("xml").put("path", directory.relativize(publishedXml.toAbsolutePath().normalize()).toString())
                .put("sha256", sha256(publishedXml));
        ObjectNode settings = data.putObject("settings").put("chainLength", state.getSamplerSpec().chainLength())
                .put("randomSeed", effectiveSeed)
                .put("runName", state.runName);
        ArrayNode loggers = settings.putArray("loggers");
        for (var spec : state.screenLoggerSpecs) {
            loggers.addObject().put("kind", "screen").put("logEvery", spec.logEvery()).putNull("path");
        }
        var files = new ArrayList<String>();
        for (var spec : state.fileLoggerSpecs) files.add(spec.fileName());
        for (var spec : state.treeLoggerSpecs) files.add(spec.fileName());
        new OutputPaths(Path.of(""), overwrite).prepare(files, null);
        for (var spec : state.fileLoggerSpecs) addLogger(loggers, "log", spec.fileName(), spec.logEvery());
        for (var spec : state.treeLoggerSpecs) addLogger(loggers, "tree", spec.fileName(), spec.logEvery());
        save();
    }

    void running() throws IOException {
        data.put("status", "running").put("mcmcStartedAt", Instant.now().toString());
        save();
    }

    private void addLogger(ArrayNode loggers, String kind, String name, long every) throws IOException {
        ObjectNode logger = loggers.addObject().put("kind", kind).put("logEvery", every)
                .put("path", OutputPaths.getRecordPath(directory, Path.of(name)));
        data.withArray("expectedOutputs").add(logger.deepCopy());
    }

    void succeeded(long completedStates) throws IOException {
        long expected = data.path("settings").path("chainLength").longValue();
        data.put("completedChainLength", completedStates);
        if (completedStates != expected) {
            throw new IOException("BEAST X stopped after " + completedStates + " of " + expected + " steps.");
        }
        for (var entry : data.withArray("expectedOutputs")) {
            Path log = directory.resolve(entry.path("path").asText()).normalize();
            if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS) || Files.size(log) == 0) {
                throw new IOException("BEAST X did not produce the expected log: " + log);
            }
        }
        var xml = data.path("xml");
        if (!sha256(directory.resolve(xml.path("path").asText())).equals(xml.path("sha256").asText())) {
            throw new IOException("The saved analysis XML changed during execution.");
        }
        finish("succeeded");
    }

    void failed(Throwable failure) throws IOException {
        data.putObject("error").put("class", failure.getClass().getName()).put("message", failure.getMessage());
        finish("failed");
    }

    private void finish(String status) throws IOException {
        ArrayNode generated = data.putArray("generatedOutputs");
        for (var entry : data.withArray("expectedOutputs")) {
            String path = entry.path("path").asText();
            Path file = directory.resolve(path).normalize();
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                generated.addObject().put("path", path).put("size", Files.size(file));
            }
        }
        data.put("status", status).put("endedAt", Instant.now().toString()).put("completionConfirmed", true);
        save();
    }

    private void save() throws IOException {
        Path temporary = Files.createTempFile(directory, ".run-", ".json.tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), data);
            try {
                Files.move(temporary, directory.resolve("run.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, directory.resolve("run.json"), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(Path file) throws IOException {
        try (var input = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            input.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(input.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
