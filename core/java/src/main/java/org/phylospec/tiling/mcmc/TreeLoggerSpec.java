package org.phylospec.tiling.mcmc;

/// An engine-agnostic description of a logger that writes trees to a file.
///
/// @param <T> the general type of trees
/// @param logEvery the sampling interval, i.e. a tree is written every {@code logEvery} states
/// @param fileName the path of the file to write to
/// @param tree the tree to log, or {@code null} to log every loggable tree in the state
public record TreeLoggerSpec<T>(int logEvery, String fileName, T tree) {}
