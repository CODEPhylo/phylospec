import tiling.BeastXState;
import tiling.operators.OperatorBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the standard runtime-only configuration shared by BEAST X
 * validation paths.
 */
final class BeastXValidationSupport {

    private BeastXValidationSupport() {
    }

    static void configure(
            BeastXState state,
            Path outputPrefix,
            long seed,
            long logEvery
    ) {
        if (seed < 0) {
            throw new IllegalArgumentException(
                    "seed must not be negative."
            );
        }

        if (logEvery <= 0) {
            throw new IllegalArgumentException(
                    "logEvery must be positive."
            );
        }

        state.randomSeed =
                seed;
        state.outputPrefix =
                outputPrefix.toString();
        state.defaultLogEvery =
                logEvery;

        if (state.screenLoggerSpecs.isEmpty()) {
            state.addScreenLoggerSpec(logEvery);
        }

        if (state.fileLoggerSpecs.isEmpty()) {
            state.addFileLoggerSpec(
                    logEvery,
                    outputPrefix + ".log"
            );
        }

        if (state.treeLoggerSpecs.isEmpty()
                && !state.treePriorDistributions.isEmpty()) {
            ArrayList<String> treeNames =
                    new ArrayList<>(
                            state.treeModelsByPhyloSpecName.keySet()
                    );
            treeNames.sort(String::compareTo);

            if (treeNames.size() == 1) {
                state.addTreeLoggerSpec(
                        logEvery,
                        outputPrefix + ".trees",
                        treeNames
                );
            } else {
                for (String treeName : treeNames) {
                    state.addTreeLoggerSpec(
                            logEvery,
                            outputPrefix + "." + treeName + ".trees",
                            List.of(treeName)
                    );
                }
            }
        }
    }

    static void writeOperatorSummary(
            BeastXState state,
            Path operatorSummaryPath
    ) throws IOException {
        if (operatorSummaryPath == null) {
            return;
        }

        if (operatorSummaryPath.getParent() != null) {
            Files.createDirectories(
                    operatorSummaryPath.getParent()
            );
        }

        Files.write(
                operatorSummaryPath,
                new OperatorBuilder().summarize(state)
        );
    }
}
