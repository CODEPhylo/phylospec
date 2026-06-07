package tiling.model;

import dr.inference.model.CompoundLikelihood;
import dr.inference.model.Likelihood;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.List;

public class ModelBuilder {

    private final boolean materializePhyloCTMC;

    public ModelBuilder() {
        this(false);
    }

    public ModelBuilder(boolean materializePhyloCTMC) {
        this.materializePhyloCTMC = materializePhyloCTMC;
    }

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

    private void materializePhyloCTMCLikelihoods(BeastXState beastState) {
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