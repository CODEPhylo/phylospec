import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeastXComparisonCommonYuleJc69StrictClockTest {

    @Test
    public void runsCommonYuleJc69StrictClockComparisonModelAndWritesLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "tiling",
                        "comparison",
                        "commonYuleJc69StrictClock.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path logPath =
                outputDirectory.resolve("common-yule-jc69-strictclock-beastx.log");

        Path treeLogPath =
                outputDirectory.resolve("common-yule-jc69-strictclock-beastx.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                stripLineComments(Files.readString(sourcePath));

        MCMC mcmc;

        try {
            mcmc =
                    new PhyloSpecRunner(source)
                            .runMaterializedMCMC("commonYuleJc69StrictClockBeastX");
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    isMissingBeagleLibrary(exception),
                    "Skipping BEAST X comparison MCMC run because BEAGLE native library is not available."
            );

            throw exception;
        }

        assertNotNull(mcmc);

        assertTrue(
                Files.exists(logPath),
                "Expected BEAST X comparison parameter log to be written: " + logPath
        );

        assertTrue(
                Files.size(logPath) > 0,
                "Expected BEAST X comparison parameter log to be non-empty: " + logPath
        );

        assertTrue(
                Files.exists(treeLogPath),
                "Expected BEAST X comparison tree log to be written: " + treeLogPath
        );

        assertTrue(
                Files.size(treeLogPath) > 0,
                "Expected BEAST X comparison tree log to be non-empty: " + treeLogPath
        );
    }

    private static String stripLineComments(String source) {
        StringBuilder cleaned =
                new StringBuilder();

        for (String line : source.split("\\R")) {
            if (!line.stripLeading().startsWith("//")) {
                cleaned.append(line).append(System.lineSeparator());
            }
        }

        return cleaned.toString();
    }

    private static boolean isMissingBeagleLibrary(Throwable throwable) {
        Throwable current =
                throwable;

        while (current != null) {
            String message =
                    current.getMessage();

            if (
                    message != null
                            && message.contains("No acceptable BEAGLE library plugins found")
            ) {
                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }

    @Test
    public void buildsCommonYuleJc69StrictClockStateWithoutRunningMCMC() throws Exception {
        String source = Files.readString(Path.of(
                "src/test/java/tiling/comparison/commonYuleJc69StrictClock.phylospec"
        ));

        PhyloSpecRunner runner = new PhyloSpecRunner(source);
        BeastXState state = runner.buildState("commonYuleJc69StrictClock");

        assertTrue(state.stateNodesByPhyloSpecName.containsKey("clockRate"));
        assertTrue(state.treeModelsByPhyloSpecName.containsKey("tree"));

        assertEquals(1, state.priorDistributions.size());
        assertEquals(1, state.treePriorDistributions.size());
        assertEquals(1, state.likelihoodDistributions.size());

        assertEquals(10000, state.chainLength);

        assertEquals(1, state.screenLoggerSpecs.size());
        assertEquals(1, state.fileLoggerSpecs.size());
        assertEquals(1, state.treeLoggerSpecs.size());

        assertEquals(
                "target/comparison/common-yule-jc69-strictclock-beastx.log",
                state.fileLoggerSpecs.get(0).fileName
        );

        assertEquals(
                "target/comparison/common-yule-jc69-strictclock-beastx.trees",
                state.treeLoggerSpecs.get(0).fileName
        );
    }

    @Test
    public void buildsMaterializedMCMCWithoutRunningIt() throws Exception {
        String source = Files.readString(Path.of(
                "src/test/java/tiling/comparison/commonYuleJc69StrictClock.phylospec"
        ));

        PhyloSpecRunner runner = new PhyloSpecRunner(source);

        BeastXModel model = runner.buildMaterializedModel(
                "commonYuleJc69StrictClock"
        );

        assertNotNull(model.prior);
        assertNotNull(model.likelihood);
        assertNotNull(model.posterior);

        assertEquals(1, model.beastState.stateNodesByPhyloSpecName.size());
        assertTrue(model.beastState.stateNodesByPhyloSpecName.containsKey("clockRate"));

        assertEquals(1, model.beastState.treeModelsByPhyloSpecName.size());
        assertTrue(model.beastState.treeModelsByPhyloSpecName.containsKey("tree"));

        assertEquals(1, model.beastState.priorDistributions.size());
        assertEquals(1, model.beastState.treePriorDistributions.size());
        assertEquals(1, model.beastState.likelihoodDistributions.size());

        MCMC mcmc = runner.buildMCMC(model, 1);

        assertNotNull(mcmc);
    }
}