package tiling;

import dr.inference.loggers.Logger;
import dr.inference.mcmc.MCMC;
import dr.inference.mcmc.MCMCOptions;
import dr.inference.model.AbstractModelLikelihood;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.SimpleOperatorSchedule;
import tiling.operators.BeastXOperatorBuilder;

import java.util.List;

public class BeastXMCMCBuilder {

    private final long chainLength;

    public BeastXMCMCBuilder() {
        this(1);
    }

    public BeastXMCMCBuilder(long chainLength) {
        if (chainLength < 0) {
            throw new IllegalArgumentException("chainLength must be non-negative.");
        }

        this.chainLength =
                chainLength;
    }

    public MCMC build(BeastXModel model) {
        rejectUnmaterializedPhyloCTMCLikelihoods(model);

        MCMC mcmc =
                new MCMC(model.beastState.getAvailableID("mcmc"));

        MCMCOptions options =
                new MCMCOptions(this.chainLength);

        SimpleOperatorSchedule operatorSchedule =
                new SimpleOperatorSchedule();

        List<MCMCOperator> operators =
                new BeastXOperatorBuilder().build(model.beastState);

        operatorSchedule.addOperators(operators);

        Logger[] loggers =
                new Logger[0];

        mcmc.init(
                options,
                model.posterior,
                operatorSchedule,
                loggers
        );

        return mcmc;
    }

    private void rejectUnmaterializedPhyloCTMCLikelihoods(BeastXModel model) {
        for (AbstractModelLikelihood likelihood : model.beastState.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec) {
                throw new IllegalStateException(
                        "Cannot build BEAST X MCMC for a model containing an unmaterialized PhyloCTMC likelihood. " +
                                "Materialize BeastXPhyloCTMCLikelihoodSpec before MCMC initialization."
                );
            }
        }
    }
}