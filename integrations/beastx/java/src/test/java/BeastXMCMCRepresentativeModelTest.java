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
        Path fileLogPath =
                uniqueTargetPath("strictClockPhyloCTMC-config", ".log");

        Path treeLogPath =
                uniqueTargetPath("strictClockPhyloCTMC-config", ".trees");

        String source =
                readSourceWithLogFiles(fileLogPath, treeLogPath);

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
        assertEquals(toPhyloSpecPath(fileLogPath), state.fileLoggerSpecs.getFirst().fileName);
        assertEquals(List.of("clockRate"), state.fileLoggerSpecs.getFirst().parameterNames);

        assertEquals(1, state.treeLoggerSpecs.size());
        assertEquals(1000, state.treeLoggerSpecs.getFirst().logEvery);
        assertEquals(toPhyloSpecPath(treeLogPath), state.treeLoggerSpecs.getFirst().fileName);
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

        try {
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
        } finally {
            stopLoggersQuietly(loggers);
        }
    }

    @Test
    public void writesNonEmptyFileAndTreeLogsWhenLoggersAreTriggered() throws Exception {
        Path fileLogPath =
                uniqueTargetPath("strictClockPhyloCTMC-write", ".log");

        Path treeLogPath =
                uniqueTargetPath("strictClockPhyloCTMC-write", ".trees");

        String source =
                readSourceWithLogFiles(fileLogPath, treeLogPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXState state =
                runner.buildState("test");

        List<Logger> loggers =
                new BeastXMCMCBuilder().buildLoggers(state);

        try {
            for (Logger logger : loggers) {
                logger.startLogging();
                logger.log(0);
            }
        } finally {
            stopLoggersQuietly(loggers);
        }

        assertNonEmptyFile(fileLogPath);
        assertNonEmptyFile(treeLogPath);
    }

    private Path uniqueTargetPath(String prefix, String suffix) throws Exception {
        Files.createDirectories(Path.of("target", "mcmc-logger-smoke"));

        return Path.of(
                "target",
                "mcmc-logger-smoke",
                prefix + "-" + System.nanoTime() + suffix
        );
    }

    private void stopLoggersQuietly(List<Logger> loggers) {
        for (Logger logger : loggers) {
            try {
                logger.stopLogging();
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void assertNonEmptyFile(Path path) throws Exception {
        assertTrue(
                Files.exists(path),
                "Expected log file to exist: " + path
        );

        assertTrue(
                Files.size(path) > 0,
                "Expected log file to be non-empty: " + path
        );
    }

    private String readSourceWithLogFiles(Path fileLogPath, Path treeLogPath) throws Exception {
        return readSource(MODEL_PATH)
                .replace(
                        "target/strictClockPhyloCTMC.log",
                        toPhyloSpecPath(fileLogPath)
                )
                .replace(
                        "target/strictClockPhyloCTMC.trees",
                        toPhyloSpecPath(treeLogPath)
                );
    }

    private String readSource(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }

    private String toPhyloSpecPath(Path path) {
        return path.toString().replace("\\", "/");
    }
}