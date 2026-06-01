import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXShowcaseRuntimeSmokeTest {

    @Test
    public void runsPriorOnlyBirthDeathMCMCAndWritesParameterAndTreeLogs() throws Exception {
        Path logPath =
                uniqueTargetPath("priorOnlyBirthDeathMCMC", ".log");

        Path treePath =
                uniqueTargetPath("priorOnlyBirthDeathMCMC", ".trees");

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Rate diversificationRate ~ LogNormal(
                    logMean=0.0,
                    logSd=0.5
                )

                Rate turnover ~ LogNormal(
                    logMean=-1.0,
                    logSd=0.25
                )

                Tree tree ~ BirthDeath(
                    diversificationRate=diversificationRate,
                    turnover=turnover,
                    samplingProbability=1.0,
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 5

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[diversificationRate, turnover]
                    )

                    Logger treeLogger = treeLogger(
                        logEvery=1,
                        file="%s",
                        trees=[tree]
                    )
                }
                """.formatted(
                        toPhyloSpecPath(logPath),
                        toPhyloSpecPath(treePath)
                );

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        MCMC mcmc =
                runner.runMCMC("test");

        assertTrue(
                mcmc != null,
                "Expected PhyloSpecRunner.runMCMC to return the executed MCMC object."
        );

        assertLogFileContainsMultipleSamples(
                logPath,
                "diversificationRate",
                "turnover"
        );

        assertTreeFileContainsMultipleTrees(treePath);
    }

    @Test
    public void runsPriorOnlySkylineMCMCWithOutputPrefixAndWritesAutoLogs() throws Exception {
        Path outputPrefix =
                uniqueTargetPrefix("priorOnlySkylineMCMC");

        Path logPath =
                Path.of(outputPrefix + ".log");

        Path treePath =
                Path.of(outputPrefix + ".trees");

        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Vector<PositiveReal> populationSizes ~ IID(
                    base=LogNormal(logMean=4.5, logSd=0.75),
                    num=4
                )

                Real totalPopulationSize = sum(vector=populationSizes)
                Real meanPopulationSize = totalPopulationSize / 4.0

                Tree tree ~ SkylineCoalescent(
                    populationSizes=populationSizes,
                    changeTimes=[0.5, 1.5, 3.0],
                    taxa=taxa
                )

                mcmc {
                    Integer chainLength = 5
                    Integer randomSeed = 20260601
                    Integer defaultLogEvery = 1
                    String outputPrefix = "%s"
                }
                """.formatted(
                        toPhyloSpecPath(outputPrefix)
                );

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        MCMC mcmc =
                runner.runMCMC("test");

        assertTrue(
                mcmc != null,
                "Expected PhyloSpecRunner.runMCMC to return the executed MCMC object."
        );

        assertLogFileContainsMultipleSamples(
                logPath,
                "posterior",
                "prior",
                "populationSizes",
                "totalPopulationSize",
                "meanPopulationSize"
        );

        assertTreeFileContainsMultipleTrees(treePath);
    }

    private void assertLogFileContainsMultipleSamples(
            Path logPath,
            String... expectedColumns
    ) throws Exception {
        assertTrue(
                Files.exists(logPath),
                "Expected MCMC log file to exist: " + logPath
        );

        assertTrue(
                Files.size(logPath) > 0,
                "Expected MCMC log file to be non-empty: " + logPath
        );

        List<String> lines =
                Files.readAllLines(logPath);

        for (String expectedColumn : expectedColumns) {
            assertTrue(
                    lines.stream()
                            .anyMatch(line -> line.contains("state") && line.contains(expectedColumn)),
                    "Expected log header to contain state and " + expectedColumn + " columns."
            );
        }

        long sampleLineCount =
                lines.stream()
                        .map(String::trim)
                        .filter(line -> line.matches("\\d+\\s+.*"))
                        .count();

        assertTrue(
                sampleLineCount >= 2,
                "Expected MCMC run to write more than one sample line."
        );
    }

    private void assertTreeFileContainsMultipleTrees(Path treePath) throws Exception {
        assertTrue(
                Files.exists(treePath),
                "Expected MCMC tree file to exist: " + treePath
        );

        assertTrue(
                Files.size(treePath) > 0,
                "Expected MCMC tree file to be non-empty: " + treePath
        );

        List<String> lines =
                Files.readAllLines(treePath);

        assertTrue(
                lines.stream().anyMatch(line -> line.contains("#NEXUS")),
                "Expected tree log to contain a NEXUS header."
        );

        long treeLineCount =
                lines.stream()
                        .map(String::trim)
                        .filter(line -> line.startsWith("tree STATE_"))
                        .count();

        assertTrue(
                treeLineCount >= 2,
                "Expected MCMC tree log to contain more than one sampled tree."
        );
    }

    private Path uniqueTargetPath(String prefix, String suffix) throws Exception {
        Files.createDirectories(Path.of("target", "showcase-runtime-smoke"));

        return Path.of(
                "target",
                "showcase-runtime-smoke",
                prefix + "-" + System.nanoTime() + suffix
        );
    }

    private Path uniqueTargetPrefix(String prefix) throws Exception {
        Files.createDirectories(Path.of("target", "showcase-runtime-smoke"));

        return Path.of(
                "target",
                "showcase-runtime-smoke",
                prefix + "-" + System.nanoTime()
        );
    }

    private String toPhyloSpecPath(Path path) {
        return path.toString().replace("\\", "/");
    }
}