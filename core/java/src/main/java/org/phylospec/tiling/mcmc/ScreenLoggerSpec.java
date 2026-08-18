package org.phylospec.tiling.mcmc;

import java.util.List;

/// An engine-agnostic description of a logger that writes to the screen.
///
/// @param <T> the general type of loggable objects
/// @param logEvery the sampling interval, i.e. a log line is written every {@code logEvery} states
/// @param parameters the objects to log, or {@code null} to log every loggable object in the state
public record ScreenLoggerSpec<T>(int logEvery, List<T> parameters) {}
