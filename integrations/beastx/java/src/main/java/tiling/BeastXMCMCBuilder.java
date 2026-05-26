package tiling;

import dr.evomodel.tree.TreeLogger;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.loggers.TabDelimitedFormatter;
import dr.inference.mcmc.MCMC;
import dr.inference.mcmc.MCMCOptions;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.SimpleOperatorSchedule;
import tiling.operators.BeastXOperatorBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class BeastXMCMCBuilder {

    private final Long chainLengthOverride;

    public BeastXMCMCBuilder() {
        this.chainLengthOverride = null;
    }

    public BeastXMCMCBuilder(long chainLength) {
        if (chainLength < 0) {
            throw new IllegalArgumentException("chainLength must be non-negative.");
        }

        this.chainLengthOverride = chainLength;
    }

    public MCMC build(BeastXModel model) {
        rejectUnmaterializedPhyloCTMCLikelihoods(model);

        MCMC mcmc =
                new MCMC(model.beastState.getAvailableID("mcmc"));

        MCMCOptions options =
                new MCMCOptions(getChainLength(model));

        SimpleOperatorSchedule operatorSchedule =
                new SimpleOperatorSchedule();

        List<MCMCOperator> operators =
                new BeastXOperatorBuilder().build(model.beastState);

        operatorSchedule.addOperators(operators);

        Logger[] loggers =
                buildLoggers(model.beastState).toArray(new Logger[0]);

        mcmc.init(
                options,
                model.posterior,
                operatorSchedule,
                loggers
        );

        return mcmc;
    }

    public List<Logger> buildLoggers(BeastXState beastState) {
        List<Logger> loggers =
                new ArrayList<>(beastState.mcmcLoggers);

        for (BeastXState.ScreenLoggerSpec spec : beastState.screenLoggerSpecs) {
            MCLogger logger =
                    new MCLogger(new TabDelimitedFormatter(System.out), spec.logEvery, true);

            for (Parameter parameter : getLoggedParameters(beastState, spec.parameterNames)) {
                logger.add(parameter);
            }

            loggers.add(logger);
        }

        for (BeastXState.FileLoggerSpec spec : beastState.fileLoggerSpecs) {
            MCLogger logger =
                    buildFileLogger(spec);

            for (Parameter parameter : getLoggedParameters(beastState, spec.parameterNames)) {
                logger.add(parameter);
            }

            loggers.add(logger);
        }

        for (BeastXState.TreeLoggerSpec spec : beastState.treeLoggerSpecs) {
            for (TreeModel treeModel : getLoggedTrees(beastState, spec.treeNames)) {
                loggers.add(buildTreeLogger(spec, treeModel));
            }
        }

        return loggers;
    }

    private MCLogger buildFileLogger(BeastXState.FileLoggerSpec spec) {
        try {
            return new MCLogger(spec.fileName, spec.logEvery, true, 0);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create BEAST X file logger for '" + spec.fileName + "'.",
                    e
            );
        }
    }

    private TreeLogger buildTreeLogger(BeastXState.TreeLoggerSpec spec, TreeModel treeModel) {
        try {
            return new TreeLogger(
                    treeModel,
                    new TabDelimitedFormatter(new PrintWriter(new FileWriter(spec.fileName))),
                    Math.toIntExact(spec.logEvery),
                    true,
                    true,
                    false
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create BEAST X tree logger for '" + spec.fileName + "'.",
                    e
            );
        }
    }

    private List<Parameter> getLoggedParameters(
            BeastXState beastState,
            List<String> parameterNames
    ) {
        if (parameterNames == null) {
            return new ArrayList<>(beastState.stateNodes.keySet());
        }

        List<Parameter> parameters =
                new ArrayList<>();

        for (String parameterName : parameterNames) {
            parameters.add(findStateNode(beastState, parameterName));
        }

        return parameters;
    }

    private List<TreeModel> getLoggedTrees(
            BeastXState beastState,
            List<String> treeNames
    ) {
        List<TreeModel> trees =
                new ArrayList<>();

        for (String treeName : treeNames) {
            trees.add(findTreeModel(beastState, treeName));
        }

        return trees;
    }

    private Parameter findStateNode(BeastXState beastState, String parameterName) {
        Parameter parameter =
                beastState.stateNodesByPhyloSpecName.get(parameterName);

        if (parameter != null) {
            return parameter;
        }

        for (Parameter candidate : beastState.stateNodes.keySet()) {
            if (parameterName.equals(candidate.getId())) {
                return candidate;
            }
        }

        throw new IllegalArgumentException(
                "No BEAST X state node named '" + parameterName + "' exists for logger."
        );
    }

    private TreeModel findTreeModel(BeastXState beastState, String treeName) {
        TreeModel treeModel =
                beastState.treeModelsByPhyloSpecName.get(treeName);

        if (treeModel != null) {
            return treeModel;
        }

        for (TreeModel candidate : beastState.treePriorDistributions.keySet()) {
            if (treeName.equals(candidate.getId())) {
                return candidate;
            }
        }

        throw new IllegalArgumentException(
                "No BEAST X tree model named '" + treeName + "' exists for treeLogger."
        );
    }

    private long getChainLength(BeastXModel model) {
        if (this.chainLengthOverride != null) {
            return this.chainLengthOverride;
        }

        return model.beastState.chainLength;
    }

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