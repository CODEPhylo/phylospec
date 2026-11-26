import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.McmcSamplerSpec;
import org.phylospec.tiling.mcmc.RunRecord;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import tiling.BeastXState;

/** Checks the BEAST X additions to the shared run record. */
class BeastXRunRecordTest {
    @TempDir Path directory;
    private RunRecord record;
    private Path recordDirectory;
    private Path xml;
    private Path output;

    @BeforeEach void prepare() throws IOException {
        Path source = Files.writeString(directory.resolve("original.phylospec"), "Real x = 1\n");
        output = Files.createDirectory(directory.resolve("output"));
        recordDirectory = Files.createDirectory(directory.resolve("analysis.run"));
        xml = Files.writeString(recordDirectory.resolve("analysis.xml"), "<beast/>\n");
        record = BeastXRunTool.createRunRecord(recordDirectory, source, Files.readString(source));
    }

    @Test void confirmsTheNativeLengthAndBothLogsBeforeRecordingSuccess() throws IOException {
        BeastXState state = new BeastXState("analysis");
        state.setSamplerSpec(new McmcSamplerSpec(10));
        state.addFileLoggerSpec(new FileLoggerSpec<>(2, output.resolve("results/trace.log").toString(), List.of("posterior"), null));
        state.addTreeLoggerSpec(new TreeLoggerSpec<>(2, output.resolve("tree.trees").toString(), "tree"));
        String xmlHash = BeastXRunTool.configure(record, state, xml, 101L, false);
        JsonNode prepared = read();
        assertEquals("beastx", prepared.at("/runtime/engine").asText());
        assertEquals("Real x = 1\n", Files.readString(recordDirectory.resolve("model.phylospec")));
        assertEquals(101L, prepared.at("/settings/randomSeed").asLong());
        assertEquals("analysis.xml", prepared.at("/xml/path").asText());
        assertEquals(xmlHash, prepared.at("/xml/sha256").asText());

        record.markRunning();
        assertThrows(IOException.class, () -> BeastXRunTool.checkResults(record, 10, 5, xml, xmlHash));
        Files.writeString(output.resolve("results/trace.log"), "state\tposterior\n10\t-1\n");
        assertThrows(IOException.class, () -> BeastXRunTool.checkResults(record, 10, 10, xml, xmlHash));
        Files.writeString(output.resolve("tree.trees"), "tree STATE_10 = (A:1,B:1);\n");
        BeastXRunTool.checkResults(record, 10, 10, xml, xmlHash);
        record.markSucceeded();

        JsonNode result = read();
        assertEquals("succeeded", result.path("status").asText());
        assertEquals(10, result.path("completedChainLength").asLong());
        assertEquals(2, result.path("generatedOutputs").size());
    }

    @Test void alteredAnalysisCannotBeReportedAsSuccessful() throws IOException {
        BeastXState state = new BeastXState("analysis");
        String xmlHash = BeastXRunTool.configure(record, state, xml, 101L, false);
        Files.writeString(xml, "<changed/>\n");
        long chainLength = state.getSamplerSpec().chainLength();
        IOException failure = assertThrows(
                IOException.class, () -> BeastXRunTool.checkResults(record, chainLength, chainLength, xml, xmlHash));
        assertTrue(failure.getMessage().contains("XML"));
    }

    @Test void loggersCannotWriteIntoTheRunRecordEvenWithOverwrite() throws IOException {
        for (Path file : List.of(recordDirectory.resolve("run.json"), xml, recordDirectory.resolve("notes.log"))) {
            BeastXState state = new BeastXState("analysis");
            state.addFileLoggerSpec(new FileLoggerSpec<>(1, file.toString(), List.of(), null));
            assertThrows(IllegalArgumentException.class, () -> BeastXRunTool.configure(record, state, xml, 101L, true), file.toString());
        }
        assertEquals("<beast/>\n", Files.readString(xml));
        assertFalse(Files.exists(recordDirectory.resolve("notes.log")));
    }

    @Test void existingOutputIsNeverOverwritten() throws IOException {
        Path existing = Files.writeString(output.resolve("existing.log"), "keep");
        BeastXState state = new BeastXState("analysis");
        state.addFileLoggerSpec(new FileLoggerSpec<>(1, existing.toString(), List.of(), null));
        assertThrows(IOException.class, () -> BeastXRunTool.configure(record, state, xml, 101L, false));
        assertEquals("keep", Files.readString(existing));
    }

    @Test void duplicateLoggerPathsAreRejected() {
        BeastXState state = new BeastXState("analysis");
        state.addFileLoggerSpec(new FileLoggerSpec<>(1, output.resolve("same.log").toString(), List.of(), null));
        state.addTreeLoggerSpec(new TreeLoggerSpec<>(1, output.resolve("nested/../same.log").toString(), "tree"));
        assertThrows(IllegalArgumentException.class, () -> BeastXRunTool.configure(record, state, xml, 101L, false));
        assertFalse(Files.exists(output.resolve("same.log")));
    }

    private JsonNode read() throws IOException {
        return new ObjectMapper().readTree(recordDirectory.resolve("run.json").toFile());
    }
}
