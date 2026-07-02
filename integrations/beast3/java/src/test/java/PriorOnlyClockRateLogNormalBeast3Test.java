import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration-test")
public class PriorOnlyClockRateLogNormalBeast3Test {

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
                        "beast3-prior-only-clockrate-lognormal.log"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourcePath));

        runner.runPhyloSpec(
                "target/comparison-diagnostics/beast3-prior-only-clockrate-lognormal"
        );

        assertTrue(Files.exists(logPath), "Expected BEAST 3 prior-only log.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST 3 prior-only log to be non-empty.");
    }
}