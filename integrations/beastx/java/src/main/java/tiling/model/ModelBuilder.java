package tiling.model;

import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.runner.BeagleBackendConfigurator;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles an in-memory BEAST X model from a completed {@link BeastXState}.
 *
 * <p>The resulting {@link BeastXModel} contains the compound prior, likelihood,
 * and posterior objects, and can later be passed to the MCMC builder.</p>
 */
public class ModelBuilder {

    private final boolean materializePhyloCTMC;

    public ModelBuilder() {
        this(false);
    }

    public ModelBuilder(boolean materializePhyloCTMC) {
        this.materializePhyloCTMC = materializePhyloCTMC;
    }

    /**
     * Builds the compound prior, likelihood, and posterior objects from the
     * completed backend state.
     */
    public BeastXModel build(BeastXState beastState) {
        if (this.materializePhyloCTMC) {
            materializePhyloCTMCLikelihoods(beastState);
        }

        CompoundLikelihood prior =
                buildPrior(beastState);

        CompoundLikelihood likelihood =
                buildLikelihood(beastState);

        CompoundLikelihood posterior =
                buildPosterior(beastState, prior, likelihood);

        return new BeastXModel(
                beastState,
                prior,
                likelihood,
                posterior
        );
    }

    /**
     * Converts deferred PhyloCTMC likelihood specifications into concrete
     * BEAST X tree likelihood objects.
     */
    private void materializePhyloCTMCLikelihoods(BeastXState beastState) {
        BeagleBackendConfigurator.requireNativeBackend();

        List<Likelihood> materializedLikelihoods =
                new ArrayList<>();

        for (Likelihood likelihood : beastState.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec phyloCTMCLikelihoodSpec) {
                Likelihood materializedLikelihood =
                        phyloCTMCLikelihoodSpec.materializeBeagleTreeLikelihood();

                materializedLikelihood.setId(phyloCTMCLikelihoodSpec.getId());
                materializedLikelihoods.add(materializedLikelihood);
            } else {
                materializedLikelihoods.add(likelihood);
            }
        }

        beastState.likelihoodDistributions.clear();
        beastState.likelihoodDistributions.addAll(materializedLikelihoods);
    }

    /**
     * Combines parameter priors, tree priors, and calibration priors into one
     * compound prior likelihood.
     */
    private CompoundLikelihood buildPrior(BeastXState beastState) {
        List<Likelihood> priorLikelihoods =
                new ArrayList<>();

        priorLikelihoods.addAll(beastState.priorDistributions.values());
        priorLikelihoods.addAll(beastState.treePriorDistributions.values());
        priorLikelihoods.addAll(beastState.calibrationPriorDistributions);

        CompoundLikelihood prior =
                new CompoundLikelihood(priorLikelihoods);

        prior.setId(beastState.getAvailableID("prior"));

        return prior;
    }

    /**
     * Combines all data likelihood components into one compound likelihood.
     */
    private CompoundLikelihood buildLikelihood(BeastXState beastState) {
        CompoundLikelihood likelihood =
                new CompoundLikelihood(new ArrayList<>(beastState.likelihoodDistributions));

        likelihood.setId(beastState.getAvailableID("likelihood"));

        return likelihood;
    }

    /**
     * Combines the compound prior and compound likelihood into the posterior
     * target used by BEAST X MCMC.
     */
    private CompoundLikelihood buildPosterior(
            BeastXState beastState,
            CompoundLikelihood prior,
            CompoundLikelihood likelihood
    ) {
        List<Likelihood> posteriorLikelihoods =
                new ArrayList<>();

        posteriorLikelihoods.add(prior);
        posteriorLikelihoods.add(likelihood);

        CompoundLikelihood posterior =
                new CompoundLikelihood(posteriorLikelihoods);

        posterior.setId(beastState.getAvailableID("posterior"));

        return posterior;
    }
}