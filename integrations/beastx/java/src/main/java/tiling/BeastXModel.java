package tiling;

import dr.inference.model.CompoundLikelihood;

/**
 * In-memory BEAST X model assembled from a completed BeastXState.
 *
 * Holds the posterior, prior, likelihood, state nodes, operators, loggers,
 * and MCMC settings used for direct BEAST X execution.
 */
public class BeastXModel {

    public final BeastXState beastState;
    public final CompoundLikelihood prior;
    public final CompoundLikelihood likelihood;
    public final CompoundLikelihood posterior;

    public BeastXModel(
            BeastXState beastState,
            CompoundLikelihood prior,
            CompoundLikelihood likelihood,
            CompoundLikelihood posterior
    ) {
        this.beastState =
                beastState;

        this.prior =
                prior;

        this.likelihood =
                likelihood;

        this.posterior =
                posterior;
    }
}
