package tiling.mcmc;

import dr.inference.loggers.Logger;
import dr.inference.mcmc.MCMC;
import dr.inference.mcmc.MCMCOptions;
import dr.inference.model.Likelihood;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.SimpleOperatorSchedule;
import dr.math.MathUtils;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.model.BeastXPhyloCTMCLikelihoodSpec;
import tiling.operators.OperatorBuilder;

import java.util.List;

/**
 * Builds runnable BEAST X MCMC objects from an in-memory {@link BeastXModel}.
 *
 * Connects the model, operators, loggers, chain length, and random seed
 * into the MCMC structure expected by BEAST X.
 */
public class MCMCBuilder {

    private final Long chainLengthOverride;

    public MCMCBuilder() {
        this.chainLengthOverride = null;
    }

    public MCMCBuilder(long chainLength) {
        if (chainLength < 0) {
            throw new IllegalArgumentException("chainLength must be non-negative.");
        }

        this.chainLengthOverride = chainLength;
    }

    public MCMC build(BeastXModel model) {
        rejectUnmaterializedPhyloCTMCLikelihoods(model);
        applyRandomSeed(model.beastState);

        MCMC mcmc =
                new MCMC(model.beastState.getAvailableID("mcmc"));

        MCMCOptions options =
                new MCMCOptions(getChainLength(model));

        SimpleOperatorSchedule operatorSchedule =
                new SimpleOperatorSchedule();

        List<MCMCOperator> operators =
                new OperatorBuilder().build(model.beastState);

        operatorSchedule.addOperators(operators);

        Logger[] loggers =
                new LoggerBuilder().build(model).toArray(new Logger[0]);

        mcmc.init(
                options,
                model.posterior,
                operatorSchedule,
                loggers
        );

        return mcmc;
    }

    public List<Logger> buildLoggers(BeastXState beastState) {
        return new LoggerBuilder().build(beastState);
    }

    public List<Logger> buildLoggers(BeastXModel model) {
        return new LoggerBuilder().build(model);
    }

    private void applyRandomSeed(BeastXState beastState) {
        if (beastState.randomSeed != null) {
            MathUtils.setSeed(beastState.randomSeed);
        }
    }

    private long getChainLength(BeastXModel model) {
        if (this.chainLengthOverride != null) {
            return this.chainLengthOverride;
        }

        return model.beastState.chainLength;
    }

    /**
     * Ensures that all PhyloCTMC likelihood specifications have been converted
     * into concrete BEAST X likelihood objects before MCMC initialization.
     */
    private void rejectUnmaterializedPhyloCTMCLikelihoods(BeastXModel model) {
        for (Likelihood likelihood : model.beastState.likelihoodDistributions) {
            if (likelihood instanceof BeastXPhyloCTMCLikelihoodSpec) {
                throw new IllegalStateException(
                        "Cannot build BEAST X MCMC for a model containing an unmaterialized PhyloCTMC likelihood. " +
                                "Materialize BeastXPhyloCTMCLikelihoodSpec before MCMC initialization."
                );
            }
        }
    }
}
