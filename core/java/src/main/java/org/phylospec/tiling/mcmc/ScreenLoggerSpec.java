package org.phylospec.tiling.mcmc;

import java.util.List;

/// An engine-agnostic description of a logger that writes to the screen.
///
/// @param <T> the general type of loggable objects
/// @param logEvery the sampling interval, i.e. a log line is written every {@code logEvery} states
/// @param parameters the objects to log, or {@code null} to log every loggable object in the state
/// @param parameterNames the names used in the log for the parameters, or {@code null} to use their own names
public record ScreenLoggerSpec<T>(int logEvery, List<T> parameters, List<String> parameterNames) implements LoggerSpec {

    public ScreenLoggerSpec {
        LoggerSpec.validateLogEvery(logEvery);
        LoggerSpec.validateParameterNames(parameters, parameterNames);
    }
}
