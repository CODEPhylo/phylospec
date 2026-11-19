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
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import tiling.BeastXState;

class BeastXRunRecordTest {
    @TempDir Path directory;
    private BeastXRunRecord record;
    private Path xml;
    private Path output;

    @BeforeEach void prepare() throws IOException {
        Path source = Files.writeString(directory.resolve("original.phylospec"), "Real x = 1\n");
        output = Files.createDirectory(directory.resolve("output"));
        xml = Files.writeString(directory.resolve("analysis.xml"), "<beast/>\n");
        record = BeastXRunRecord.create(directory, source);
    }

    @Test void confirmsTheNativeLengthAndBothLogsBeforeRecordingSuccess() throws IOException {
        BeastXState state = new BeastXState("analysis");
        state.setSamplerSpec(new McmcSamplerSpec(10));
        state.addFileLoggerSpec(new FileLoggerSpec<>(2, "./results/trace.log", List.of("posterior"), null));
        state.addTreeLoggerSpec(new TreeLoggerSpec<>(2, "tree.trees", "tree"));
        Path data = Files.writeString(directory.resolve("data.txt"), "abc");
        record.input(data);
        record.input(data);
        record.seed("modelBuilding", null, 101L);
        record.seed("mcmc", null, 101L);
        record.configure(state, xml, 101L);
        JsonNode prepared = read();
        assertEquals("preparing", prepared.path("status").asText());
        assertFalse(prepared.has("mcmcStartedAt"));
        assertEquals(101L, prepared.at("/settings/randomSeed").asLong());
        assertTrue(prepared.at("/seeds/mcmc/requested").isNull());
        record.running();
        assertEquals("running", read().path("status").asText());
        assertFalse(read().path("completionConfirmed").asBoolean());
        assertThrows(IOException.class, () -> record.succeeded(5));
        Files.writeString(output.resolve("results/trace.log"), "state\tposterior\n10\t-1\n");
        assertThrows(IOException.class, () -> record.succeeded(10));
        Files.writeString(output.resolve("tree.trees"), "tree STATE_10 = (A:1,B:1);\n");
        record.succeeded(10);
        JsonNode result = read();
        assertEquals("succeeded", result.path("status").asText());
        assertTrue(result.path("completionConfirmed").asBoolean());
        assertEquals(10, result.path("completedChainLength").asLong());
        assertEquals("beastx", result.at("/runtime/engine").asText());
        assertEquals(101, result.at("/seeds/mcmc/observed").asLong());
        assertEquals(2, result.path("generatedOutputs").size());
        assertEquals(1, result.path("inputs").size());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.at("/inputs/0/sha256").asText());
    }

    @Test void alteredAnalysisCannotBeReportedAsSuccessful() throws IOException {
        BeastXState state = new BeastXState("analysis");
        record.configure(state, xml, 101L);
        record.running();
        Files.writeString(xml, "<changed/>\n");
        IOException failure = assertThrows(IOException.class, () -> record.succeeded(state.getSamplerSpec().chainLength()));
        record.failed(failure);
        JsonNode result = read();
        assertEquals("failed", result.path("status").asText());
        assertTrue(result.path("completionConfirmed").asBoolean());
        assertTrue(result.at("/error/message").asText().contains("XML"));
    }

    @Test void rejectsLoggerPathsOutsideOutput() throws IOException {
        for (String name : List.of("../run.json", "../analysis.xml", xml.toString(), ".//" + xml.getFileName())) {
            BeastXState state = new BeastXState("analysis");
            state.addFileLoggerSpec(new FileLoggerSpec<>(1, name, List.of(), null));
            assertThrows(IOException.class, () -> record.configure(state, xml, 101L), name);
            assertEquals("<beast/>\n", Files.readString(xml));
        }
    }

    @Test void existingOutputIsNeverOverwritten() throws IOException {
        Path existing = Files.writeString(output.resolve("existing.log"), "keep");
        BeastXState state = new BeastXState("analysis");
        state.addFileLoggerSpec(new FileLoggerSpec<>(1, "existing.log", List.of(), null));
        assertThrows(IOException.class, () -> record.configure(state, xml, 101L));
        assertEquals("keep", Files.readString(existing));
    }

    @Test void duplicateLoggerPathsAreRejected() {
        BeastXState state = new BeastXState("analysis");
        state.addFileLoggerSpec(new FileLoggerSpec<>(1, "same.log", List.of(), null));
        state.addTreeLoggerSpec(new TreeLoggerSpec<>(1, "./same.log", "tree"));
        assertThrows(IOException.class, () -> record.configure(state, xml, 101L));
        assertFalse(Files.exists(output.resolve("same.log")));
    }

    private JsonNode read() throws IOException {
        return new ObjectMapper().readTree(directory.resolve("run.json").toFile());
    }
}
