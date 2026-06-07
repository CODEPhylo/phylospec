import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tiling.mcmc.MCMCBuilder;
import tiling.BeastXModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXMaterializedPhyloCTMCMCMCTest {

    private static final Path MODEL_PATH =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "tiling",
                    "representative",
                    "coverage",
                    "strictClockPhyloCTMCWithMCMC.phylospec"
            );

    @Test
    public void runsShortMaterializedPhyloCTMCMCMCWhenBeagleIsAvailable() throws Exception {
        Path fileLogPath =
                uniqueTargetPath("materializedPhyloCTMC", ".log");

        Path treeLogPath =
                uniqueTargetPath("materializedPhyloCTMC", ".trees");

        String source =
                readSourceWithLogFiles(fileLogPath, treeLogPath)
                        .replace("Integer chainLength = 10000", "Integer chainLength = 3")
                        .replace("logEvery=1000", "logEvery=1");

        BeastXModel model =
                assumeMaterializedModelCanBeBuilt(source);

        MCMC mcmc =
                new MCMCBuilder(3).build(model);

        mcmc.run();

        assertNonEmptyFile(fileLogPath);
        assertNonEmptyFile(treeLogPath);

        List<String> parameterLogLines =
                Files.readAllLines(fileLogPath);

        assertTrue(
                parameterLogLines.stream().anyMatch(line -> line.contains("state") && line.contains("clockRate")),
                "Expected parameter log header to contain state and clockRate."
        );

        long parameterSampleLineCount =
                parameterLogLines.stream()
                        .map(String::trim)
                        .filter(line -> line.matches("\\d+\\s+.*"))
                        .count();

        assertTrue(
                parameterSampleLineCount >= 2,
                "Expected materialized PhyloCTMC MCMC to write multiple parameter samples."
        );

        List<String> treeLogLines =
                Files.readAllLines(treeLogPath);

        long treeSampleLineCount =
                treeLogLines.stream()
                        .map(String::trim)
                        .filter(line -> line.startsWith("tree STATE_"))
                        .count();

        assertTrue(
                treeSampleLineCount >= 2,
                "Expected materialized PhyloCTMC MCMC to write multiple tree samples."
        );
    }

    private BeastXModel assumeMaterializedModelCanBeBuilt(String source) throws Exception {
        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        try {
            return runner.buildMaterializedModel("test");
        } catch (RuntimeException error) {
            if (containsMessage(error, "No acceptable BEAGLE library plugins found")
                    || containsMessage(error, "no hmsbeagle64")) {
                Assumptions.assumeTrue(
                        false,
                        "Skipping materialized PhyloCTMC MCMC smoke test because BEAGLE native library is not available."
                );
            }

            throw error;
        }
    }

    private boolean containsMessage(Throwable error, String expectedText) {
        Throwable current =
                error;

        while (current != null) {
            String message =
                    current.getMessage();

            if (message != null && message.contains(expectedText)) {
                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }

    private Path uniqueTargetPath(String prefix, String suffix) throws Exception {
        Files.createDirectories(Path.of("target", "materialized-phyloctmc-mcmc-smoke"));

        return Path.of(
                "target",
                "materialized-phyloctmc-mcmc-smoke",
                prefix + "-" + System.nanoTime() + suffix
        );
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