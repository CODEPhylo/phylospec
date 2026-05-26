import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import org.junit.jupiter.api.Test;
import tiling.BeastXMCMCBuilder;
import tiling.BeastXState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXMCMCRepresentativeModelTest {

    private static final Path MODEL_PATH =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "tiling",
                    "representative",
                    "strictClockPhyloCTMCWithMCMC.phylospec"
            );

    @Test
    public void buildsStrictClockPhyloCTMCWithMCMCConfiguration() throws Exception {
        String source =
                readSource(MODEL_PATH);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXState state =
                runner.buildState("test");

        assertEquals(10000, state.chainLength);

        assertEquals(1, state.screenLoggerSpecs.size());
        assertEquals(1000, state.screenLoggerSpecs.getFirst().logEvery);
        assertEquals(List.of("clockRate"), state.screenLoggerSpecs.getFirst().parameterNames);

        assertEquals(1, state.fileLoggerSpecs.size());
        assertEquals(1000, state.fileLoggerSpecs.getFirst().logEvery);
        assertEquals("target/strictClockPhyloCTMC.log", state.fileLoggerSpecs.getFirst().fileName);
        assertEquals(List.of("clockRate"), state.fileLoggerSpecs.getFirst().parameterNames);

        assertEquals(1, state.treeLoggerSpecs.size());
        assertEquals(1000, state.treeLoggerSpecs.getFirst().logEvery);
        assertEquals("target/strictClockPhyloCTMC.trees", state.treeLoggerSpecs.getFirst().fileName);
        assertEquals(List.of("tree"), state.treeLoggerSpecs.getFirst().treeNames);

        assertEquals(1, state.stateNodesByPhyloSpecName.size());
        assertTrue(state.stateNodesByPhyloSpecName.containsKey("clockRate"));

        assertEquals(1, state.treeModelsByPhyloSpecName.size());
        assertTrue(state.treeModelsByPhyloSpecName.containsKey("tree"));

        assertEquals(1, state.priorDistributions.size());
        assertEquals(1, state.treePriorDistributions.size());
        assertEquals(1, state.likelihoodDistributions.size());

        List<Logger> loggers =
                new BeastXMCMCBuilder().buildLoggers(state);

        assertEquals(3, loggers.size());

        List<MCLogger> parameterLoggers =
                loggers.stream()
                        .filter(MCLogger.class::isInstance)
                        .filter(logger -> !(logger instanceof dr.evomodel.tree.TreeLogger))
                        .map(MCLogger.class::cast)
                        .toList();

        assertEquals(2, parameterLoggers.size());

        for (MCLogger logger : parameterLoggers) {
            assertEquals(1000, logger.getLogEvery());
            assertEquals(1, logger.getColumnCount());
            assertEquals("clockRate", logger.getColumnLabel(0));
        }

        long treeLoggerCount =
                loggers.stream()
                        .filter(dr.evomodel.tree.TreeLogger.class::isInstance)
                        .count();

        assertEquals(1, treeLoggerCount);
    }

    private String readSource(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }
}