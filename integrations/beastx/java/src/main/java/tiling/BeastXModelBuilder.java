package tiling;

import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;

import java.util.ArrayList;
import java.util.List;

public class BeastXModelBuilder {

    public BeastXModel build(BeastXState beastState) {
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

    private CompoundLikelihood buildPrior(BeastXState beastState) {
        List<Likelihood> priorLikelihoods =
                new ArrayList<>();

        priorLikelihoods.addAll(beastState.priorDistributions.values());
        priorLikelihoods.addAll(beastState.treePriorDistributions.values());

        CompoundLikelihood prior =
                new CompoundLikelihood(priorLikelihoods);

        prior.setId(beastState.getAvailableID("prior"));

        return prior;
    }

    private CompoundLikelihood buildLikelihood(BeastXState beastState) {
        CompoundLikelihood likelihood =
                new CompoundLikelihood(new ArrayList<>(beastState.likelihoodDistributions));

        likelihood.setId(beastState.getAvailableID("likelihood"));

        return likelihood;
    }

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
