package runner;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.Logger;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.errors.Error;
import org.phylospec.tiling.TileCatalog;

class RunRecordTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE = "// 模型\r\nReal value = 1.0\n";

    @Test
    void snapshotsExactlyTheSourcePassedToTheRunnerAndRecordsActualMetadata(@TempDir Path directory) throws Exception {
        Path original = directory.resolve("original.phylospec");
        Files.writeString(original, "different contents now on disk");
        Path run = directory.resolve("run");
        Files.createDirectories(run.resolve("output"));
        TileCatalog<BEASTState> catalog = catalog();
        RunRecord.create(run, SOURCE, original, directory, catalog);

        assertEquals(SOURCE, Files.readString(run.resolve("model.phylospec")));
        JsonNode data = read(run);
        assertEquals(hash(SOURCE), data.path("source").path("sha256").asText());
        assertEquals(original.toString(), data.path("source").path("path").asText());
        assertEquals(directory.toString(), data.path("source").path("inputBase").asText());
        assertEquals("preparing", data.path("status").asText());
        assertFalse(data.path("completionConfirmed").asBoolean());
        assertTrue(data.path("endedAt").isNull());
        assertEquals("beast3", data.path("runtime").path("engine").asText());
        assertFalse(data.path("runtime").path("engineVersion").asText().isBlank());
        var library = catalog.getLibraries().getFirst();
        JsonNode provider = data.path("libraries").get(0);
        assertEquals(library.getId(), provider.path("id").asText());
        assertEquals(library.getClass().getName(), provider.path("class").asText());
        assertTrue(provider.has("version"));
        String version = library.getClass().getPackage().getImplementationVersion();
        if (version == null) assertTrue(provider.path("version").isNull());
        else assertEquals(version, provider.path("version").asText());
        assertEquals(
                catalog.getComponentLibraries().getFirst().getVersion(),
                data.path("components").get(0).path("version").asText());
        assertTrue(data.path("inputCoverage").asText().contains("not a self-contained archive"));
    }

    @Test
    void hashesReadableInputsAndKeepsMissingInputsExplicitWithoutThrowing(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        Path input = directory.resolve("data.nex");
        Files.writeString(input, "input contents");
        record.input("../data.nex", input);
        record.input("../data.nex", input);
        record.input("missing.nex", directory.resolve("missing.nex"));

        JsonNode inputs = read(directory).path("inputs");
        assertEquals(2, inputs.size());
        assertEquals(hash("input contents"), inputs.get(0).path("sha256").asText());
        assertEquals("recorded", inputs.get(0).path("status").asText());
        assertFalse(inputs.get(0).path("fingerprintedAt").asText().isBlank());
        assertEquals("unavailable", inputs.get(1).path("status").asText());
        assertTrue(inputs.get(1).path("sha256").isNull());
        assertFalse(inputs.get(1).path("reason").asText().isBlank());
    }

    @Test
    void separatesExpectedOutputsFromPartialFilesOnFailure(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        BEASTState state = new BEASTState("sample", directory, directory.resolve("output"));
        state.chainLength = 123;
        state.defaultLogEvery = 10;
        state.randomSeed = 27L;
        Logger screen = new Logger();
        Logger file = new Logger();
        file.fileNameInput.setValue(directory.resolve("output/custom.log").toString(), file);
        file.everyInput.setValue(7, file);
        Logger tree = new Logger();
        tree.fileNameInput.setValue(directory.resolve("output/tree.trees").toString(), tree);
        tree.modeInput.setValue(Logger.LOGMODE.tree, tree);
        String stateFile = directory.resolve("output/sample.state.xml").toString();
        record.configuration(state, List.of(screen, file, tree), stateFile);
        record.running();
        Files.writeString(directory.resolve("output/custom.log"), "partial");
        record.failed(new IOException("native file failed"));

        JsonNode data = read(directory);
        assertEquals("failed", data.path("status").asText());
        assertTrue(data.path("completionConfirmed").asBoolean());
        assertTrue(data.has("endedAt"));
        assertEquals("native file failed", data.path("error").path("message").asText());
        JsonNode settings = data.path("settings");
        assertEquals(123, settings.path("chainLength").asLong());
        assertEquals(27, settings.path("randomSeed").asLong());
        assertEquals(3, settings.path("loggers").size());
        assertEquals("screen", settings.path("loggers").get(0).path("kind").asText());
        assertEquals(3, data.path("expectedOutputs").size());
        assertEquals(7, data.path("expectedOutputs").get(0).path("logEvery").asInt());
        assertEquals(
                "output/sample.state.xml",
                data.path("expectedOutputs").get(2).path("path").asText());
        assertEquals(1, data.path("generatedOutputs").size());
        assertEquals(
                "output/custom.log",
                data.path("generatedOutputs").get(0).path("path").asText());
        assertEquals(7, data.path("generatedOutputs").get(0).path("size").asLong());
    }

    @Test
    void retainsRequestedAndObservedSeedsAndWarnings(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.seed("modelBuilding", 27L, 26, false);
        record.seed("mcmc", null, 27, false);
        PhyloSpecDiagnostic warning = new PhyloSpecDiagnostic(
                PhyloSpecDiagnostic.Stage.TYPE_CHECKING,
                new Error(null, "Check this value.", "A warning."),
                true,
                SOURCE,
                null);
        record.warnings(List.of(warning));

        JsonNode data = read(directory);
        assertEquals(
                27, data.path("seeds").path("modelBuilding").path("requested").asLong());
        assertEquals(
                26, data.path("seeds").path("modelBuilding").path("observed").asLong());
        assertFalse(data.path("seeds").path("modelBuilding").path("generated").asBoolean());
        assertEquals(27, data.path("seeds").path("mcmc").path("observed").asLong());
        assertTrue(data.path("seeds").path("mcmc").path("requested").isNull());
        assertEquals(warning.format(), data.path("warnings").get(0).asText());
        assertThrows(IllegalArgumentException.class, () -> record.seed("other", 1L, 1, true));
    }

    @Test
    void terminalSuccessCannotBeReplacedByAnUnconfirmedShutdown(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.running();
        record.succeeded();
        assertTrue(record.isTerminal());
        record.unconfirmed("process stopped");
        assertEquals("succeeded", read(directory).path("status").asText());
        assertThrows(IllegalStateException.class, record::running);
    }

    @Test
    void recordsUnconfirmedShutdownWithoutClaimingAConfirmedOutcome(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.running();
        record.unconfirmed("Process stopped before completion was recorded.");
        JsonNode data = read(directory);
        assertEquals("unconfirmed", data.path("status").asText());
        assertFalse(data.path("completionConfirmed").asBoolean());
        assertFalse(record.isTerminal());
        assertTrue(data.path("endedAt").isNull());
    }

    @Test
    void recordsCancellationAndStructuredDiagnostic(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        PhyloSpecDiagnostic diagnostic = new PhyloSpecDiagnostic(
                PhyloSpecDiagnostic.Stage.MODEL_BUILDING,
                new Error(null, "Model failed.", "Inspect this input."),
                false,
                SOURCE,
                null);
        PhyloSpecException failure = new PhyloSpecException(diagnostic, new IOException("native"), List.of());
        record.cancelled(failure);
        JsonNode data = read(directory);
        assertEquals("cancelled", data.path("status").asText());
        assertTrue(record.isTerminal());
        assertEquals(diagnostic.format(), data.path("error").path("diagnostic").asText());
    }

    @Test
    void failedManifestReplacementDoesNotCommitAnInMemorySuccess(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.running();
        Path manifest = directory.resolve("run.json");
        Files.move(manifest, directory.resolve("previous.json"));
        Files.createDirectory(manifest);
        Path blocker = Files.writeString(manifest.resolve("blocker"), "occupied");

        assertThrows(IOException.class, record::succeeded);
        assertFalse(record.isTerminal());
        Files.delete(blocker);
        Files.delete(manifest);
        record.failed(new IOException("Cannot save success"));
        assertEquals("failed", read(directory).path("status").asText());
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().startsWith(".run-")));
        }
    }

    private static RunRecord create(Path directory) throws Exception {
        Files.createDirectories(directory.resolve("output"));
        return RunRecord.create(directory, SOURCE, null, directory, catalog());
    }

    private static TileCatalog<BEASTState> catalog() throws IOException {
        return TileCatalog.select(BEASTState.class, List.of("beast2"));
    }

    private static JsonNode read(Path directory) throws IOException {
        return JSON.readTree(directory.resolve("run.json").toFile());
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
