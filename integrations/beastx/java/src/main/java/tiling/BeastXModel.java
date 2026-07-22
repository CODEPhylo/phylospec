package tiling;

import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory BEAST X model assembled from a built-up {@link BeastXState}.
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

    public static BeastXModel fromBeastXState(
            BeastXState beastState,
            boolean materializePhyloCTMC
    ) {
        if (materializePhyloCTMC) {
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

    private static void materializePhyloCTMCLikelihoods(BeastXState beastState) {
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

    private static CompoundLikelihood buildPrior(BeastXState beastState) {
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

    private static CompoundLikelihood buildLikelihood(BeastXState beastState) {
        CompoundLikelihood likelihood =
                new CompoundLikelihood(new ArrayList<>(beastState.likelihoodDistributions));

        likelihood.setId(beastState.getAvailableID("likelihood"));

        return likelihood;
    }

    private static CompoundLikelihood buildPosterior(
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
