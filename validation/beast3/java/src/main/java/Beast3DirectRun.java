import beast.base.inference.Logger;
import beast.base.inference.MCMC;
import beast.base.util.Randomizer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * BEAST 3 validation entry point that runs a PhyloSpec model directly.
 *
 * <p>This path constructs the BEAST object graph in memory and executes the
 * resulting MCMC without serializing or reparsing an XML document.</p>
 */
public final class Beast3DirectRun {

    private Beast3DirectRun() {
    }

    public static void main(String[] args) throws Exception {
        Path outputPrefix =
                ValidationConfiguration.requiredPath("outputPrefix");
        Path expectedLogPath =
                ValidationConfiguration.requiredPath("expectedLog");
        Path operatorSummaryPath =
                ValidationConfiguration.optionalPath("operatorSummary");
        long seed =
                Long.parseLong(
                        ValidationConfiguration.required("seed")
                );

        if (outputPrefix.getParent() != null) {
            Files.createDirectories(outputPrefix.getParent());
        }
        if (expectedLogPath.getParent() != null) {
            Files.createDirectories(expectedLogPath.getParent());
        }
        if (operatorSummaryPath != null
                && operatorSummaryPath.getParent() != null) {
            Files.createDirectories(operatorSummaryPath.getParent());
        }

        String source =
                ValidationConfiguration.readSource("source");

        Randomizer.setSeed(seed);

        Logger.LogFileMode previousLogFileMode =
                Logger.FILE_MODE;

        try {
            Logger.FILE_MODE =
                    Logger.LogFileMode.overwrite;

            PhyloSpecRunner runner =
                    new PhyloSpecRunner(source);
            MCMC mcmc =
                    runner.buildMCMC(
                            outputPrefix.toString(),
                            operatorSummaryPath
                    );
            long startNanos =
                    System.nanoTime();

            mcmc.run();

            double elapsedSeconds =
                    (System.nanoTime() - startNanos)
                            / 1_000_000_000.0;

            System.out.printf(
                    Locale.ROOT,
                    "%nvalidation.mcmc.wall_clock_seconds=%.6f%n",
                    elapsedSeconds
            );
        } finally {
            Logger.FILE_MODE =
                    previousLogFileMode;
        }

        if (!Files.isRegularFile(expectedLogPath)
                || Files.size(expectedLogPath) == 0) {
            throw new IllegalStateException(
                    "Expected BEAST 3 direct-run log was not generated: "
                            + expectedLogPath
            );
        }

        System.out.println(
                "Completed BEAST 3 direct run: " + expectedLogPath
        );
    }
}
