import org.junit.jupiter.api.Test;
import tiling.runner.RunMode;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                                RunnerOptions.builder("runnerFacadeState")
                                        .mode(RunMode.BUILD_STATE)
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
                                RunnerOptions.builder("runnerFacadeModel")
                                        .mode(RunMode.BUILD_MODEL)
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
                    Sampler sampler = mcmc(chainLength=5)

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(logPath.toString().replace("\\", "/"));

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);
        runner.setSeed(1234L);

        BeastXRunResult result =
                runner
                        .run(
                                RunnerOptions.builder("runnerFacadeExecution")
                                        .mode(RunMode.EXECUTE_MCMC)
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

    @Test
    public void sameSeedReproducesTheSampledValues() throws Exception {
        Path outputDirectory =
                Path.of("target", "runner-facade");

        Files.createDirectories(outputDirectory);

        List<List<String>> runs =
                new ArrayList<>();

        for (String run : List.of("first", "second")) {
            Path logPath =
                    outputDirectory.resolve("seeded-" + run + "-" + System.nanoTime() + ".log");

            String source = """
                    Real x ~ Normal(mean=0.0, sd=1.0)

                    mcmc {
                        Sampler sampler = mcmc(chainLength=50)
                        Logger fileLogger = fileLogger(file="%s", logEvery=1, parameters=[x])
                    }
                    """.formatted(logPath.toString().replace("\\", "/"));

            PhyloSpecRunner runner =
                    new PhyloSpecRunner(source);
            runner.setSeed(2468L);
            runner.runMCMC("seededRun");

            try (Stream<String> lines = Files.lines(logPath)) {
                runs.add(lines.filter(line -> line.matches("\\d+\\s.*")).toList());
            }
        }

        assertEquals(51, runs.getFirst().size());
        assertTrue(
                runs.getFirst().stream().map(line -> line.split("\\s+")[1]).distinct().count() > 1,
                "Expected the chain to move."
        );
        assertEquals(runs.getFirst(), runs.getLast());
    }
}
