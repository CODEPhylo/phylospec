package org.phylospec.tiling.mcmc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.phylospec.tiling.errors.TileApplicationError;

/// An engine-agnostic description of a logger.
public interface LoggerSpec {

    int DEFAULT_LOG_EVERY = 1000;

    /// The columns every screen and file logger writes.
    Set<String> ALWAYS_LOGGED = Set.of("posterior", "prior", "likelihood");

    /// Returns the sampling interval, i.e. something is logged every {@code logEvery} states.
    int logEvery();

    /* validation of the values which the types cannot express */

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

        Set<String> seen = new HashSet<>();
        for (String name : parameterNames) {
            if (ALWAYS_LOGGED.contains(name)) {
                throw new TileApplicationError(
                        "The parameter name `" + name + "` clashes with the `" + name
                                + "` column, which every screen and file logger writes.",
                        "Choose another name in 'parameterNames'.");
            }
            if (!seen.add(name)) {
                throw new TileApplicationError(
                        "The parameter name `" + name + "` is used more than once.",
                        "Give every entry of 'parameterNames' its own name.");
            }
        }
    }
}
