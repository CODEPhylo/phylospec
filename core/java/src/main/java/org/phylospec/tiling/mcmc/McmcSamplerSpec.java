package org.phylospec.tiling.mcmc;

import org.phylospec.tiling.errors.TileApplicationError;

/// An engine-agnostic description of a simple MCMC sampler.
///
/// @param chainLength the number of states the chain runs for
public record McmcSamplerSpec(long chainLength) {

    public static final long DEFAULT_CHAIN_LENGTH = 10_000_000;

    /// Creates the sampler that is used when a model does not declare one.
    public McmcSamplerSpec() {
        this(DEFAULT_CHAIN_LENGTH);
    }

    public McmcSamplerSpec {
        if (chainLength <= 0) {
            throw new TileApplicationError(
                    "The chain length must be positive.", "Use a positive integer, for example chainLength=1000000.");
        }
    }
}
