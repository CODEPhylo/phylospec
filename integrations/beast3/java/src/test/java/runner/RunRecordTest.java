package runner;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.Logger;
import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.mcmc.RunRecord;

/** Checks the BEAST 3 additions to the shared run record. */
class RunRecordTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void recordsTheBeast3RuntimeLibrariesAndComponents(@TempDir Path directory) throws Exception {
        TileCatalog<BEASTState> catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        PhyloSpecRunner.createRunRecord(directory, "Real value = 1.0\n", null, directory, catalog, List.of());

        JsonNode data = read(directory);
        assertEquals("beast3", data.path("runtime").path("engine").asText());
        assertFalse(data.path("runtime").path("engineVersion").asText().isBlank());
        assertTrue(data.path("runtime").path("adapterBundles").isArray());

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
    void describesScreenFileAndTreeLoggers() {
        Logger screen = new Logger();
        screen.everyInput.setValue(5, screen);
        Logger file = new Logger();
        file.fileNameInput.setValue("custom.log", file);
        file.everyInput.setValue(7, file);
        Logger tree = new Logger();
        tree.fileNameInput.setValue("tree.trees", tree);
        tree.modeInput.setValue(Logger.LOGMODE.tree, tree);

        assertEquals(
                List.of(
                        new RunRecord.LoggerSetting("screen", 5, null),
                        new RunRecord.LoggerSetting("log", 7, "custom.log"),
                        new RunRecord.LoggerSetting("tree", 1, "tree.trees")),
                PhyloSpecRunner.getLoggerSettings(List.of(screen, file, tree)));
    }

    private static JsonNode read(Path directory) throws IOException {
        return JSON.readTree(Files.newInputStream(directory.resolve("run.json")));
    }
}
