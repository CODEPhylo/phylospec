package org.phylospec.tiling.mcmc;

import static org.junit.jupiter.api.Assertions.*;

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

class RunRecordTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE = "// 模型\r\nReal value = 1.0\n";

    @Test
    void snapshotsExactlyTheSourcePassedToTheRunner(@TempDir Path directory) throws Exception {
        Path original = directory.resolve("original.phylospec");
        Files.writeString(original, "different contents now on disk");
        Path run = Files.createDirectory(directory.resolve("run"));
        RunRecord.create(run, SOURCE, original, directory, "engine", "1.2", "0.1");

        assertEquals(SOURCE, Files.readString(run.resolve("model.phylospec")));
        JsonNode data = read(run);
        assertEquals(hash(SOURCE), data.path("source").path("sha256").asText());
        assertEquals(original.toString(), data.path("source").path("path").asText());
        assertEquals(directory.toString(), data.path("source").path("inputBase").asText());
        assertEquals("preparing", data.path("status").asText());
        assertFalse(data.path("completionConfirmed").asBoolean());
        assertTrue(data.path("endedAt").isNull());
        assertEquals("engine", data.path("runtime").path("engine").asText());
        assertEquals("1.2", data.path("runtime").path("engineVersion").asText());
        assertEquals("0.1", data.path("runtime").path("phylospecVersion").asText());
    }

    @Test
    void hashesReadableInputsAndKeepsMissingInputsExplicitWithoutThrowing(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        Path input = directory.resolve("data.nex");
        Files.writeString(input, "input contents");
        record.addInput("../data.nex", input);
        record.addInput("../data.nex", input);
        record.addInput(null, input);
        record.addInput(null, input);
        record.addInput("missing.nex", directory.resolve("missing.nex"));

        JsonNode inputs = read(directory).path("inputs");
        assertEquals(3, inputs.size());
        assertEquals(hash("input contents"), inputs.get(0).path("sha256").asText());
        assertEquals("recorded", inputs.get(0).path("status").asText());
        assertFalse(inputs.get(0).path("fingerprintedAt").asText().isBlank());
        assertTrue(inputs.get(1).path("reference").isNull());
        assertEquals("unavailable", inputs.get(2).path("status").asText());
        assertTrue(inputs.get(2).path("sha256").isNull());
        assertFalse(inputs.get(2).path("reason").asText().isBlank());
    }

    @Test
    void separatesExpectedOutputsFromPartialFilesOnFailure(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.configure(
                123,
                27L,
                "sample",
                List.of(
                        new RunRecord.LoggerSetting("screen", 1, null),
                        new RunRecord.LoggerSetting(
                                "log", 7, directory.resolve("output/custom.log").toString()),
                        new RunRecord.LoggerSetting(
                                "tree",
                                1,
                                directory.resolve("output/tree.trees").toString())),
                directory.resolve("output/sample.state.xml").toString());
        record.markRunning();
        Files.writeString(directory.resolve("output/custom.log"), "partial");
        record.markFailed(new IOException("native file failed"), "Inspect the log.");

        JsonNode data = read(directory);
        assertEquals("failed", data.path("status").asText());
        assertTrue(data.path("completionConfirmed").asBoolean());
        assertTrue(data.has("endedAt"));
        assertEquals("native file failed", data.path("error").path("message").asText());
        assertEquals("Inspect the log.", data.path("error").path("diagnostic").asText());
        JsonNode settings = data.path("settings");
        assertEquals(123, settings.path("chainLength").asLong());
        assertEquals(27, settings.path("randomSeed").asLong());
        assertEquals(3, settings.path("loggers").size());
        assertTrue(settings.path("loggers").get(0).path("path").isNull());
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
        assertEquals(
                List.of(
                        new RunRecord.OutputFile("log", directory.resolve("output/custom.log")),
                        new RunRecord.OutputFile("tree", directory.resolve("output/tree.trees")),
                        new RunRecord.OutputFile("state", directory.resolve("output/sample.state.xml"))),
                record.getOutputFiles());
    }

    @Test
    void omitsTheStateFileIfThereIsNone(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.configure(
                10,
                1L,
                "sample",
                List.of(new RunRecord.LoggerSetting(
                        "log", 1, directory.resolve("output/trace.log").toString())),
                null);

        assertEquals(1, read(directory).path("expectedOutputs").size());
    }

    @Test
    void retainsRequestedAndObservedSeedsAndWarnings(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.recordSeed("modelBuilding", 27L, 26, false);
        record.recordSeed("mcmc", null, 27, true);
        record.addWarning("Check this value.");

        JsonNode data = read(directory);
        assertEquals(27, data.at("/seeds/modelBuilding/requested").asLong());
        assertEquals(26, data.at("/seeds/modelBuilding/observed").asLong());
        assertFalse(data.at("/seeds/modelBuilding/generated").asBoolean());
        assertEquals(27, data.at("/seeds/mcmc/observed").asLong());
        assertTrue(data.at("/seeds/mcmc/requested").isNull());
        assertTrue(data.at("/seeds/mcmc/generated").asBoolean());
        assertEquals("Check this value.", data.path("warnings").get(0).asText());
    }

    @Test
    void appliesEngineSpecificUpdates(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.update(data -> data.putObject("xml").put("path", "analysis.xml"));

        assertEquals("analysis.xml", read(directory).at("/xml/path").asText());
    }

    @Test
    void terminalSuccessCannotBeReplacedByAnUnconfirmedShutdown(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.markRunning();
        record.markSucceeded();
        assertTrue(record.isTerminal());
        record.markUnconfirmed("process stopped");
        assertEquals("succeeded", read(directory).path("status").asText());
        assertThrows(IllegalStateException.class, record::markRunning);
        assertThrows(IllegalStateException.class, () -> record.update(data -> data.put("late", true)));
    }

    @Test
    void recordsUnconfirmedShutdownWithoutClaimingAConfirmedOutcome(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.markRunning();
        record.markUnconfirmed("Process stopped before completion was recorded.");

        JsonNode data = read(directory);
        assertEquals("unconfirmed", data.path("status").asText());
        assertFalse(data.path("completionConfirmed").asBoolean());
        assertFalse(record.isTerminal());
        assertTrue(data.path("endedAt").isNull());
    }

    @Test
    void recordsCancellation(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.markCancelled(new IOException("native"), null);

        JsonNode data = read(directory);
        assertEquals("cancelled", data.path("status").asText());
        assertTrue(record.isTerminal());
        assertFalse(data.path("error").has("diagnostic"));
    }

    @Test
    void failedManifestReplacementDoesNotCommitAnInMemorySuccess(@TempDir Path directory) throws Exception {
        RunRecord record = create(directory);
        record.markRunning();
        Path manifest = directory.resolve("run.json");
        Files.move(manifest, directory.resolve("previous.json"));
        Files.createDirectory(manifest);
        Path blocker = Files.writeString(manifest.resolve("blocker"), "occupied");

        assertThrows(IOException.class, record::markSucceeded);
        assertFalse(record.isTerminal());
        Files.delete(blocker);
        Files.delete(manifest);
        record.markFailed(new IOException("Cannot save success"), null);
        assertEquals("failed", read(directory).path("status").asText());
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().startsWith(".run-")));
        }
    }

    private static RunRecord create(Path directory) throws Exception {
        Files.createDirectories(directory.resolve("output"));
        return RunRecord.create(directory, SOURCE, null, directory, "engine", "1.2", "0.1");
    }

    private static JsonNode read(Path directory) throws IOException {
        return JSON.readTree(directory.resolve("run.json").toFile());
    }

    private static String hash(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
