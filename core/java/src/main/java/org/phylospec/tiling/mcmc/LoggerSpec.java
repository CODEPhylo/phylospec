package org.phylospec.tiling.mcmc;

import java.util.List;
import org.phylospec.tiling.errors.TileApplicationError;

/// An engine-agnostic description of a logger.
public interface LoggerSpec {

    int DEFAULT_LOG_EVERY = 1000;

    /// Returns the sampling interval, i.e. something is logged every {@code logEvery} states.
    int logEvery();

    /* validation shared by the logger specs */

    static void validateLogEvery(int logEvery) {
        if (logEvery <= 0) {
            throw new TileApplicationError(
                    "The logging interval must be positive.", "Use a positive integer, for example logEvery=1000.");
        }
    }

    static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new TileApplicationError(
                    "The logger has no file to write to.", "Pass a file name, for example file=\"output.log\".");
        }
    }

    static void validateParameterNames(List<?> parameters, List<String> parameterNames) {
        if (parameterNames == null) return;

        if (parameters == null) {
            throw new TileApplicationError(
                    "The logger has parameter names but no parameters.",
                    "Pass the logged parameters with 'parameters' as well, or remove 'parameterNames'.");
        }

        if (parameters.size() != parameterNames.size()) {
            throw new TileApplicationError(
                    "The logger has " + parameters.size() + " parameters but " + parameterNames.size()
                            + " parameter names.",
                    "Pass exactly one name in 'parameterNames' for every entry of 'parameters'.");
        }
    }
}
