import dr.inference.mcmc.MCMC;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * BEAST X validation entry point that runs a PhyloSpec model directly.
 */
public final class BeastXDirectRun {

    private BeastXDirectRun() {
    }

    public static void main(String[] args) throws Exception {
        String runName =
                ValidationConfiguration.required("runName");
        Path outputPrefix =
                ValidationConfiguration.requiredPath("outputPrefix");
        Path expectedLogPath =
                ValidationConfiguration.requiredPath("expectedLog");
        Path operatorSummaryPath =
                ValidationConfiguration.optionalPath("operatorSummary");
        long seed =
                ValidationConfiguration.requiredLong("seed");
        long logEvery =
                ValidationConfiguration.requiredLong("logEvery");

        if (outputPrefix.getParent() != null) {
            Files.createDirectories(outputPrefix.getParent());
        }

        String source =
                ValidationConfiguration.readSource("source");

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);
        BeastXState state =
                runner.buildState(runName);

        BeastXValidationSupport.configure(
                state,
                outputPrefix,
                seed,
                logEvery
        );
        BeastXValidationSupport.writeOperatorSummary(
                state,
                operatorSummaryPath
        );

        BeastXModel model =
                runner.buildMaterializedModel(state);
        MCMC mcmc =
                runner.buildMCMC(model);

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

        if (!Files.isRegularFile(expectedLogPath)
                || Files.size(expectedLogPath) == 0) {
            throw new IllegalStateException(
                    "Expected BEAST X direct-run log was not generated: "
                            + expectedLogPath
            );
        }

        System.out.println(
                "Completed BEAST X direct run: " + expectedLogPath
        );
    }
}
