package tiling.mcmc;

import dr.evomodel.tree.TreeLogger;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.Loggable;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.loggers.TabDelimitedFormatter;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import tiling.BeastXModel;
import tiling.BeastXState;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LoggerBuilder {

    public List<Logger> build(BeastXState beastState) {
        return build(null, beastState);
    }

    public List<Logger> build(BeastXModel model) {
        return build(model, model.beastState);
    }

    private List<Logger> build(BeastXModel model, BeastXState beastState) {
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
                    new MCLogger(new TabDelimitedFormatter(System.out), spec.logEvery(), true);

            for (Loggable loggable : getLoggedLoggables(model, beastState, spec.parameterNames())) {
                logger.add(loggable);
            }

            loggers.add(logger);
        }

        for (BeastXState.FileLoggerSpec spec : beastState.fileLoggerSpecs) {
            MCLogger logger =
                    buildFileLogger(spec);

            for (Loggable loggable : getLoggedLoggables(model, beastState, spec.parameterNames())) {
                logger.add(loggable);
            }

            loggers.add(logger);
        }

        for (BeastXState.TreeLoggerSpec spec : beastState.treeLoggerSpecs) {
            for (TreeModel treeModel : getLoggedTrees(beastState, spec.treeNames())) {
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
                    new MCLogger(new TabDelimitedFormatter(System.out), screenLoggerSpec.logEvery(), true);

            for (Loggable loggable : getLoggedLoggables(model, beastState, screenLoggerSpec.parameterNames())) {
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

            for (TreeModel treeModel : getLoggedTrees(beastState, treeLoggerSpec.treeNames())) {
                loggers.add(buildTreeLogger(treeLoggerSpec, treeModel));
            }
        }
    }

    private MCLogger buildFileLogger(BeastXState.FileLoggerSpec spec) {
        try {
            ensureParentDirectoryExists(spec.fileName());

            return new MCLogger(spec.fileName(), spec.logEvery(), false, 0);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create BEAST X file logger for '" + spec.fileName() + "'.",
                    e
            );
        }
    }

    private TreeLogger buildTreeLogger(BeastXState.TreeLoggerSpec spec, TreeModel treeModel) {
        try {
            ensureParentDirectoryExists(spec.fileName());

            return new TreeLogger(
                    treeModel,
                    new TabDelimitedFormatter(new PrintWriter(new FileWriter(spec.fileName()))),
                    Math.toIntExact(spec.logEvery()),
                    true,
                    true,
                    false
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create BEAST X tree logger for '" + spec.fileName() + "'.",
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

        for (String treeName : beastState.treeModelsByPhyloSpecName.keySet()) {
            TreeModel treeModel =
                    beastState.treeModelsByPhyloSpecName.get(treeName);

            loggables.add(TreeStatisticsLoggable.all(treeModel, treeName));
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

        Loggable treeStatistic =
                findTreeStatisticLoggable(beastState, loggableName);

        if (treeStatistic != null) {
            return treeStatistic;
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

        Loggable likelihoodComponent =
                findLikelihoodComponent(beastState, loggableName);

        if (likelihoodComponent != null) {
            return likelihoodComponent;
        }

        throw new IllegalArgumentException(
                "No BEAST X loggable named '" + loggableName + "' exists for logger."
        );
    }

    private Loggable findLikelihoodComponent(
            BeastXState beastState,
            String loggableName
    ) {
        for (Likelihood candidate : beastState.priorDistributions.values()) {
            if (loggableName.equals(candidate.getId())) {
                return candidate;
            }
        }

        for (Likelihood candidate : beastState.treePriorDistributions.values()) {
            if (loggableName.equals(candidate.getId())) {
                return candidate;
            }
        }

        for (Likelihood candidate : beastState.calibrationPriorDistributions) {
            if (loggableName.equals(candidate.getId())) {
                return candidate;
            }
        }

        for (Likelihood candidate : beastState.likelihoodDistributions) {
            if (loggableName.equals(candidate.getId())) {
                return candidate;
            }
        }

        return null;
    }

    private Loggable findTreeStatisticLoggable(
            BeastXState beastState,
            String loggableName
    ) {
        if (loggableName.endsWith(".height")) {
            String treeName =
                    loggableName.substring(0, loggableName.length() - ".height".length());

            TreeModel treeModel =
                    beastState.treeModelsByPhyloSpecName.get(treeName);

            if (treeModel != null) {
                return TreeStatisticsLoggable.height(treeModel, treeName);
            }
        }

        if (loggableName.endsWith(".treeLength")) {
            String treeName =
                    loggableName.substring(0, loggableName.length() - ".treeLength".length());

            TreeModel treeModel =
                    beastState.treeModelsByPhyloSpecName.get(treeName);

            if (treeModel != null) {
                return TreeStatisticsLoggable.treeLength(treeModel, treeName);
            }
        }

        return null;
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
}
