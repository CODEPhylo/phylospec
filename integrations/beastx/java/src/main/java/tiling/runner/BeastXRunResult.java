package tiling.runner;

import dr.inference.mcmc.MCMC;
import tiling.BeastXModel;
import tiling.BeastXState;

public record BeastXRunResult(
        String runName,
        RunnerOptions options,
        BeastXState beastState,
        BeastXModel model,
        MCMC mcmc,
        boolean materialized,
        boolean executed
) {

    public BeastXRunResult {
        if (runName == null || runName.isBlank()) {
            throw new IllegalArgumentException("runName must not be blank.");
        }

        if (options == null) {
            throw new IllegalArgumentException("options must not be null.");
        }

        if (beastState == null) {
            throw new IllegalArgumentException("beastState must not be null.");
        }
    }

    public boolean hasModel() {
        return this.model != null;
    }

    public boolean hasMCMC() {
        return this.mcmc != null;
    }

    public BeastXRunResult asExecuted() {
        if (this.mcmc == null) {
            throw new IllegalStateException("Cannot mark a run as executed when no MCMC object was built.");
        }

        return new BeastXRunResult(
                this.runName,
                this.options,
                this.beastState,
                this.model,
                this.mcmc,
                this.materialized,
                true
        );
    }
}