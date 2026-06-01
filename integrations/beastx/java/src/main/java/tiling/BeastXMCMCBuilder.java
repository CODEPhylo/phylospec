package tiling;

import dr.evomodel.tree.TreeLogger;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.Loggable;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.loggers.TabDelimitedFormatter;
import dr.inference.mcmc.MCMC;
import dr.inference.mcmc.MCMCOptions;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.SimpleOperatorSchedule;
import dr.math.MathUtils;
import tiling.operators.BeastXOperatorBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
        applyRandomSeed(model.beastState);

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
                buildLoggers(model).toArray(new Logger[0]);

        mcmc.init(
                options,
                model.posterior,
                operatorSchedule,
                loggers
        );

        return mcmc;
    }

    public List<Logger> buildLoggers(BeastXState beastState) {
        return buildLoggers(null, beastState);
    }

    public List<Logger> buildLoggers(BeastXModel model) {
        return buildLoggers(model, model.beastState);
    }

    private List<Logger> buildLoggers(BeastXModel model, BeastXState beastState) {
        List<Logger> loggers =
                new ArrayList<>(beastState.mcmcLoggers);

        addConfiguredLoggers(model, beastState, loggers);
        addOutputPrefixLoggers(model, beastState, loggers);

        return loggers;
    }

    private void addConfiguredLoggers(
            BeastXModel model,
            BeastXState beastState,
            List<Logger> loggers
    ) {
        for (BeastXState.ScreenLoggerSpec spec : beastState.screenLoggerSpecs) {
            MCLogger logger =
                    new MCLogger(new TabDelimitedFormatter(System.out), spec.logEvery, true);

            for (Loggable loggable : getLoggedLoggables(model, beastState, spec.parameterNames)) {
                logger.add(loggable);
            }

            loggers.add(logger);
        }

        for (BeastXState.FileLoggerSpec spec : beastState.fileLoggerSpecs) {
            MCLogger logger =
                    buildFileLogger(spec);

            for (Loggable loggable : getLoggedLoggables(model, beastState, spec.parameterNames)) {
                logger.add(loggable);
            }

            loggers.add(logger);
        }

        for (BeastXState.TreeLoggerSpec spec : beastState.treeLoggerSpecs) {
            for (TreeModel treeModel : getLoggedTrees(beastState, spec.treeNames)) {
                loggers.add(buildTreeLogger(spec, treeModel));
            }
        }
    }

    private void addOutputPrefixLoggers(
            BeastXModel model,
            BeastXState beastState,
            List<Logger> loggers
    ) {
        if (beastState.outputPrefix == null) {
            return;
        }

        if (beastState.screenLoggerSpecs.isEmpty()) {
            BeastXState.ScreenLoggerSpec screenLoggerSpec =
                    new BeastXState.ScreenLoggerSpec(
                            beastState.defaultLogEvery,
                            null
                    );

            MCLogger logger =
                    new MCLogger(new TabDelimitedFormatter(System.out), screenLoggerSpec.logEvery, true);

            for (Loggable loggable : getLoggedLoggables(model, beastState, screenLoggerSpec.parameterNames)) {
                logger.add(loggable);
            }

            loggers.add(logger);
        }

        if (beastState.fileLoggerSpecs.isEmpty()) {
            BeastXState.FileLoggerSpec fileLoggerSpec =
                    new BeastXState.FileLoggerSpec(
                            beastState.defaultLogEvery,
                            beastState.outputPrefix + ".log",
                            null
                    );

            MCLogger logger =
                    buildFileLogger(fileLoggerSpec);

            for (Loggable loggable : getLoggedLoggables(model, beastState, null)) {
                logger.add(loggable);
            }

            loggers.add(logger);
        }

        if (beastState.treeLoggerSpecs.isEmpty() && !beastState.treePriorDistributions.isEmpty()) {
            BeastXState.TreeLoggerSpec treeLoggerSpec =
                    new BeastXState.TreeLoggerSpec(
                            beastState.defaultLogEvery,
                            beastState.outputPrefix + ".trees",
                            new ArrayList<>(beastState.treeModelsByPhyloSpecName.keySet())
                    );

            for (TreeModel treeModel : getLoggedTrees(beastState, treeLoggerSpec.treeNames)) {
                loggers.add(buildTreeLogger(treeLoggerSpec, treeModel));
            }
        }
    }

    private void applyRandomSeed(BeastXState beastState) {
        if (beastState.randomSeed != null) {
            MathUtils.setSeed(beastState.randomSeed);
        }
    }

    private MCLogger buildFileLogger(BeastXState.FileLoggerSpec spec) {
        try {
            ensureParentDirectoryExists(spec.fileName);

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
            ensureParentDirectoryExists(spec.fileName);

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

    private void ensureParentDirectoryExists(String fileName) throws IOException {
        Path path =
                Path.of(fileName);

        Path parent =
                path.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private List<Loggable> getLoggedLoggables(
            BeastXModel model,
            BeastXState beastState,
            List<String> loggableNames
    ) {
        if (loggableNames == null) {
            return getDefaultLoggedLoggables(model, beastState);
        }

        List<Loggable> loggables =
                new ArrayList<>();

        for (String loggableName : loggableNames) {
            loggables.add(findLoggable(model, beastState, loggableName));
        }

        return loggables;
    }

    private List<Loggable> getDefaultLoggedLoggables(
            BeastXModel model,
            BeastXState beastState
    ) {
        List<Loggable> loggables =
                new ArrayList<>();

        if (model != null) {
            loggables.add(model.posterior);
            loggables.add(model.prior);
            loggables.add(model.likelihood);
        }

        loggables.addAll(beastState.stateNodes.keySet());
        loggables.addAll(beastState.calculationNodes.keySet());

        return loggables;
    }

    private Loggable findLoggable(
            BeastXModel model,
            BeastXState beastState,
            String loggableName
    ) {
        if (model != null) {
            switch (loggableName) {
                case "posterior" -> {
                    return model.posterior;
                }
                case "prior" -> {
                    return model.prior;
                }
                case "likelihood" -> {
                    return model.likelihood;
                }
                default -> {
                }
            }
        }

        if (model == null && isModelLevelLoggable(loggableName)) {
            throw new IllegalArgumentException(
                    "Model-level loggable '" + loggableName + "' requires a BeastXModel. " +
                            "Use buildLoggers(BeastXModel) or buildMCMC(BeastXModel)."
            );
        }

        Parameter parameter =
                beastState.stateNodesByPhyloSpecName.get(loggableName);

        if (parameter != null) {
            return parameter;
        }

        for (Parameter candidate : beastState.stateNodes.keySet()) {
            if (loggableName.equals(candidate.getId())) {
                return candidate;
            }
        }

        Statistic statistic =
                beastState.calculationNodesByPhyloSpecName.get(loggableName);

        if (statistic != null) {
            return statistic;
        }

        for (Statistic candidate : beastState.calculationNodes.keySet()) {
            if (loggableName.equals(candidate.getId())) {
                return candidate;
            }
        }

        throw new IllegalArgumentException(
                "No BEAST X loggable named '" + loggableName + "' exists for logger."
        );
    }

    private static boolean isModelLevelLoggable(String loggableName) {
        return loggableName.equals("posterior")
                || loggableName.equals("prior")
                || loggableName.equals("likelihood");
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