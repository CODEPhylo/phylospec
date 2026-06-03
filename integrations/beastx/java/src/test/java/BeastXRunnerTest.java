import org.junit.jupiter.api.Test;
import tiling.runner.BeastXRunMode;
import tiling.runner.BeastXRunResult;
import tiling.runner.BeastXRunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXRunnerTest {

    @Test
    public void buildStateModeStopsAfterStateConstruction() throws Exception {
        String source = """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
                """;

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                BeastXRunnerOptions.builder("runnerFacadeState")
                                        .mode(BeastXRunMode.BUILD_STATE)
                                        .build()
                        );

        assertNotNull(result.beastState());
        assertFalse(result.hasModel());
        assertFalse(result.hasMCMC());
        assertFalse(result.executed());
    }

    @Test
    public void buildModelModeStopsAfterModelConstruction() throws Exception {
        String source = """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )
                """;

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                BeastXRunnerOptions.builder("runnerFacadeModel")
                                        .mode(BeastXRunMode.BUILD_MODEL)
                                        .build()
                        );

        assertNotNull(result.beastState());
        assertTrue(result.hasModel());
        assertFalse(result.hasMCMC());
        assertFalse(result.executed());
    }

    @Test
    public void executeMCMCModeRunsThroughUnifiedRunnerFacade() throws Exception {
        long suffix =
                System.nanoTime();

        Path outputDirectory =
                Path.of("target", "runner-facade");

        Path logPath =
                outputDirectory.resolve("runnerFacade-" + suffix + ".log");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(logPath);

        String source = """
                PositiveReal x ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 1234

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(logPath.toString().replace("\\", "/"));

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .run(
                                BeastXRunnerOptions.builder("runnerFacadeExecution")
                                        .mode(BeastXRunMode.EXECUTE_MCMC)
                                        .chainLengthOverride(5)
                                        .build()
                        );

        assertNotNull(result.beastState());
        assertTrue(result.hasModel());
        assertTrue(result.hasMCMC());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected runner facade execution to write a log file.");
        assertTrue(Files.size(logPath) > 0, "Expected runner facade execution log to be non-empty.");

        try (Stream<String> lines = Files.lines(logPath)) {
            assertTrue(
                    lines.count() >= 2,
                    "Expected runner facade execution log to contain a header and at least one sample."
            );
        }
    }
}