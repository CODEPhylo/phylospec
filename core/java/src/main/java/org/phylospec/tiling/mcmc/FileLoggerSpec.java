package org.phylospec.tiling.mcmc;

import java.util.List;

/// An engine-agnostic description of a logger that writes to a trace file.
///
/// @param <T> the general type of loggable objects
/// @param logEvery the sampling interval, i.e. a log line is written every {@code logEvery} states
/// @param fileName the path of the file to write to
/// @param parameters the objects to log, or {@code null} to log every loggable object in the state
/// @param parameterNames the names used in the log for the parameters, or {@code null} to use their own names
public record FileLoggerSpec<T>(int logEvery, String fileName, List<T> parameters, List<String> parameterNames)
        implements LoggerSpec {

    public FileLoggerSpec {
        LoggerSpec.validateFileName(fileName);
        LoggerSpec.validateParameterNames(parameters, parameterNames);
    }
}
