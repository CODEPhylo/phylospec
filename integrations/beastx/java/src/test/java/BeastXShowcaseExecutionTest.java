import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXShowcaseExecutionTest {

    private static final Path OUTPUT_DIR =
            Path.of("target", "showcase-execution");

    private static final Path REPORT_PATH =
            OUTPUT_DIR.resolve("BEASTX_SHOWCASE_EXECUTION.md");

    @Test
    public void executesSelectedShowcaseModelsAndWritesReport() throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        List<ExecutionResult> results =
                new ArrayList<>();

        results.add(runPriorOnlyBirthDeathMCMC());
        results.add(runPriorOnlySkylineMCMC());

        writeExecutionReport(results);

        for (ExecutionResult result : results) {
            assertTrue(
                    result.success(),
                    result.failureMessage()
            );
        }

        assertTrue(
                Files.exists(REPORT_PATH),
                "Expected showcase execution report to exist: " + REPORT_PATH
        );

        assertTrue(
                Files.size(REPORT_PATH) > 0,
                "Expected showcase execution report to be non-empty: " + REPORT_PATH
        );
    }

    private ExecutionResult runPriorOnlyBirthDeathMCMC() throws Exception {
        Path logPath =
                uniqueOutputPath("priorOnlyBirthDeathMCMC", ".log");

        Path treePath =
                uniqueOutputPath("priorOnlyBirthDeathMCMC", ".trees");

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

        return executeModel(
                "priorOnlyBirthDeathMCMC",
                source,
                logPath,
                treePath,
                List.of(
                        "diversificationRate",
                        "turnover"
                )
        );
    }

    private ExecutionResult runPriorOnlySkylineMCMC() throws Exception {
        Path outputPrefix =
                uniqueOutputPrefix("priorOnlySkylineMCMC");

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

        return executeModel(
                "priorOnlySkylineMCMC",
                source,
                logPath,
                treePath,
                List.of(
                        "posterior",
                        "prior",
                        "populationSizes",
                        "totalPopulationSize",
                        "meanPopulationSize"
                )
        );
    }

    private ExecutionResult executeModel(
            String modelName,
            String source,
            Path logPath,
            Path treePath,
            List<String> expectedLogColumns
    ) {
        try {
            PhyloSpecRunner runner =
                    new PhyloSpecRunner(source);

            MCMC mcmc =
                    runner.runMCMC(modelName);

            assertNotNull(
                    mcmc,
                    "Expected PhyloSpecRunner.runMCMC to return the executed MCMC object."
            );

            LogCheck logCheck =
                    checkLogFile(logPath, expectedLogColumns);

            TreeCheck treeCheck =
                    checkTreeFile(treePath);

            return new ExecutionResult(
                    modelName,
                    true,
                    logPath,
                    treePath,
                    logCheck.sampleLineCount(),
                    treeCheck.treeLineCount(),
                    ""
            );
        } catch (Exception exception) {
            return new ExecutionResult(
                    modelName,
                    false,
                    logPath,
                    treePath,
                    0,
                    0,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private LogCheck checkLogFile(
            Path logPath,
            List<String> expectedColumns
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
                Files.readAllLines(logPath, StandardCharsets.UTF_8);

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
                "Expected MCMC log file to contain more than one sample line: " + logPath
        );

        return new LogCheck(sampleLineCount);
    }

    private TreeCheck checkTreeFile(Path treePath) throws Exception {
        assertTrue(
                Files.exists(treePath),
                "Expected MCMC tree file to exist: " + treePath
        );

        assertTrue(
                Files.size(treePath) > 0,
                "Expected MCMC tree file to be non-empty: " + treePath
        );

        List<String> lines =
                Files.readAllLines(treePath, StandardCharsets.UTF_8);

        assertTrue(
                lines.stream().anyMatch(line -> line.contains("#NEXUS")),
                "Expected tree log to contain a NEXUS header: " + treePath
        );

        long treeLineCount =
                lines.stream()
                        .map(String::trim)
                        .filter(line -> line.startsWith("tree STATE_"))
                        .count();

        assertTrue(
                treeLineCount >= 2,
                "Expected MCMC tree log to contain more than one sampled tree: " + treePath
        );

        return new TreeCheck(treeLineCount);
    }

    private void writeExecutionReport(List<ExecutionResult> results) throws Exception {
        StringBuilder report =
                new StringBuilder();

        report.append("# BEAST X Showcase Execution\n\n");
        report.append("Generated: ").append(Instant.now()).append("\n\n");

        report.append("This report validates selected PhyloSpec-BEAST X showcase models through BEAST X Java object-level MCMC execution.\n\n");

        report.append("| Model | Status | Parameter log | Tree log | Log samples | Tree samples |\n");
        report.append("|---|---:|---|---|---:|---:|\n");

        for (ExecutionResult result : results) {
            report.append("| ")
                    .append(result.modelName())
                    .append(" | ")
                    .append(result.success() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(toPhyloSpecPath(result.logPath()))
                    .append(" | ")
                    .append(toPhyloSpecPath(result.treePath()))
                    .append(" | ")
                    .append(result.logSampleCount())
                    .append(" | ")
                    .append(result.treeSampleCount())
                    .append(" |\n");
        }

        report.append("\n");

        for (ExecutionResult result : results) {
            if (!result.success()) {
                report.append("## ")
                        .append(result.modelName())
                        .append(" failure\n\n")
                        .append("```text\n")
                        .append(result.failureMessage())
                        .append("\n```\n\n");
            }
        }

        report.append("## Interpretation\n\n");
        report.append("These checks confirm that the selected PhyloSpec scripts can be tiled, converted into BEAST X Java objects, executed by the BEAST X MCMC engine, and written to parameter and tree log files.\n");
        report.append("They validate object-level BEAST X execution, not XML-level execution.\n");

        Files.writeString(
                REPORT_PATH,
                report.toString(),
                StandardCharsets.UTF_8
        );
    }

    private Path uniqueOutputPath(String prefix, String suffix) throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        return OUTPUT_DIR.resolve(
                prefix + "-" + System.nanoTime() + suffix
        );
    }

    private Path uniqueOutputPrefix(String prefix) throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        return OUTPUT_DIR.resolve(
                prefix + "-" + System.nanoTime()
        );
    }

    private String toPhyloSpecPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    private record LogCheck(
            long sampleLineCount
    ) {
    }

    private record TreeCheck(
            long treeLineCount
    ) {
    }

    private record ExecutionResult(
            String modelName,
            boolean success,
            Path logPath,
            Path treePath,
            long logSampleCount,
            long treeSampleCount,
            String failureMessage
    ) {
    }
}
