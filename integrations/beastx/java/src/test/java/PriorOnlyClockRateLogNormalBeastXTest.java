import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PriorOnlyClockRateLogNormalBeastXTest {

    @Test
    public void runsPriorOnlyClockRateLogNormal() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "priorOnlyClockRateLogNormal.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-prior-only-clockrate-lognormal.log"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourcePath));

        runner.runMCMC("priorOnlyClockRateLogNormalBeastX");

        assertTrue(Files.exists(logPath), "Expected BEAST X prior-only log.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST X prior-only log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), "Expected posterior column in BEAST X prior-only log.");
        assertTrue(log.contains("prior"), "Expected prior column in BEAST X prior-only log.");
        assertTrue(log.contains("likelihood"), "Expected likelihood column in BEAST X prior-only log.");
        assertTrue(log.contains("clockRate_prior"), "Expected clockRate_prior column in BEAST X prior-only log.");
        assertTrue(log.contains("clockRate"), "Expected clockRate column in BEAST X prior-only log.");
    }
}
