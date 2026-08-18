package org.phylospec.tiling.tiles;

import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;

/// The state built up while tiling a PhyloSpec model.
///
/// @param <O> the general type of objects
/// @param <T> the general type of trees
public interface TiledState<O, T> {
    /**
     * Adds a given screen logger to the state.
     */
    void addScreenLoggerSpec(ScreenLoggerSpec<O> logger);

    /**
     * Adds a given file logger to the state.
     */
    void addFileLoggerSpec(FileLoggerSpec<O> logger);

    /**
     * Adds a given tree logger to the state.
     */
    void addTreeLoggerSpec(TreeLoggerSpec<T> logger);
}
