import dr.inference.mcmc.MCMC;
import org.junit.jupiter.api.Test;
import tiling.BeastXMCMCBuilder;
import tiling.BeastXModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXMCMCRunSmokeTest {

    @Test
    public void runsShortPriorOnlyMCMCAndWritesMultipleSamples() throws Exception {
        Path logPath =
                uniqueTargetPath("priorOnlyMCMC", ".log");

        String source =
                """
                PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)

                mcmc {
                    Integer chainLength = 5

                    Logger fileLogger = fileLogger(
                        logEvery=1,
                        file="%s",
                        parameters=[x]
                    )
                }
                """.formatted(toPhyloSpecPath(logPath));

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        MCMC mcmc =
                new BeastXMCMCBuilder().build(model);

        mcmc.run();

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

        assertTrue(
                lines.stream().anyMatch(line -> line.contains("state") && line.contains("x")),
                "Expected log header to contain state and x columns."
        );

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

    private Path uniqueTargetPath(String prefix, String suffix) throws Exception {
        Files.createDirectories(Path.of("target", "mcmc-run-smoke"));

        return Path.of(
                "target",
                "mcmc-run-smoke",
                prefix + "-" + System.nanoTime() + suffix
        );
    }

    private String toPhyloSpecPath(Path path) {
        return path.toString().replace("\\", "/");
    }
}