package org.phylospec.tiling.mcmc;

/// An engine-agnostic description of a simple MCMC sampler.
///
/// @param chainLength the number of states the chain runs for
/// @param stateFile the path of the file storing the final state, or {@code null} to use the default
public record McmcSamplerSpec(long chainLength, String stateFile) {

    public static final long DEFAULT_CHAIN_LENGTH = 10_000_000;

    /// Creates the sampler that is used when a model does not declare one.
    public McmcSamplerSpec() {
        this(DEFAULT_CHAIN_LENGTH);
    }

    public McmcSamplerSpec(long chainLength) {
        this(chainLength, null);
    }
}
