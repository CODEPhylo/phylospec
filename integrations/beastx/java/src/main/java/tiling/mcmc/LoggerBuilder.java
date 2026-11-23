package tiling.mcmc;

import dr.evomodel.tree.TreeLogger;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.LogColumn;
import dr.inference.loggers.Loggable;
import dr.inference.loggers.Logger;
import dr.inference.loggers.MCLogger;
import dr.inference.loggers.NumberColumn;
import dr.inference.loggers.TabDelimitedFormatter;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import tiling.BeastXModel;
import tiling.BeastXState;
import tiling.loggables.TreeStatisticsLoggable;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds BEAST X runtime loggers from logger specs collected in a {@link BeastXState} or a {@link BeastXModel}.
 */
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
        for (ScreenLoggerSpec<String> spec : beastState.screenLoggerSpecs) {
            loggers.add(buildScreenLogger(model, beastState, spec));
        }

        for (FileLoggerSpec<String> spec : beastState.fileLoggerSpecs) {
            loggers.add(buildFileLogger(model, beastState, spec));
        }

        for (TreeLoggerSpec<String> spec : beastState.treeLoggerSpecs) {
            loggers.add(buildTreeLogger(spec, findTreeModel(beastState, spec.tree())));
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
            loggers.add(buildScreenLogger(
                    model, beastState, new ScreenLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, null, null)));
        }

        if (beastState.fileLoggerSpecs.isEmpty()) {
            loggers.add(buildFileLogger(
                    model,
                    beastState,
                    new FileLoggerSpec<>(
                            LoggerSpec.DEFAULT_LOG_EVERY, beastState.outputPrefix + ".log", null, null)));
        }

        if (beastState.treeLoggerSpecs.isEmpty()) {
            for (TreeLoggerSpec<String> spec : beastState.getDefaultTreeLoggerSpecs(beastState.outputPrefix)) {
                loggers.add(buildTreeLogger(spec, findTreeModel(beastState, spec.tree())));
            }
        }
    }

    private MCLogger buildScreenLogger(BeastXModel model, BeastXState beastState, ScreenLoggerSpec<String> spec) {
        MCLogger logger =
                new MCLogger(new TabDelimitedFormatter(System.out), spec.logEvery(), true);

        for (Loggable loggable : getLoggedLoggables(model, beastState, spec.parameters(), spec.parameterNames())) {
            logger.add(loggable);
        }

        return logger;
    }

    private MCLogger buildFileLogger(BeastXModel model, BeastXState beastState, FileLoggerSpec<String> spec) {
        MCLogger logger;

        try {
            ensureParentDirectoryExists(spec.fileName());

            logger = new MCLogger(spec.fileName(), spec.logEvery(), false, 0);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not create BEAST X file logger for '" + spec.fileName() + "'.",
                    e
            );
        }

        for (Loggable loggable : getLoggedLoggables(model, beastState, spec.parameters(), spec.parameterNames())) {
            logger.add(loggable);
        }

        return logger;
    }

    private TreeLogger buildTreeLogger(TreeLoggerSpec<String> spec, TreeModel treeModel) {
        try {
            ensureParentDirectoryExists(spec.fileName());

            return new TreeLogger(
                    treeModel,
                    new TabDelimitedFormatter(new PrintWriter(new FileWriter(spec.fileName()))),
                    spec.logEvery(),
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
            List<String> loggableNames,
            List<String> labels
    ) {
        List<Loggable> loggables =
                new ArrayList<>();

        // the posterior, prior and likelihood are always logged
        if (model != null) {
            loggables.add(model.posterior);
            loggables.add(model.prior);
            loggables.add(model.likelihood);
        }

        if (loggableNames == null) {
            loggables.addAll(getDefaultLoggedLoggables(beastState));
            return loggables;
        }

        for (int i = 0; i < loggableNames.size(); i++) {
            String loggableName = loggableNames.get(i);

            Loggable loggable = findLoggable(beastState, loggableName);
            loggables.add(labels != null ? labelLoggable(loggable, labels.get(i)) : loggable);
        }

        return loggables;
    }

    /**
     * Returns a loggable with the columns of the given one under a new label. Like the BEAST X XML
     * {@code column} element, several columns are numbered starting at 1.
     */
    private Loggable labelLoggable(Loggable loggable, String label) {
        LogColumn[] columns = loggable.getColumns();

        for (int i = 0; i < columns.length; i++) {
            columns[i].setLabel(columns.length > 1 ? label + (i + 1) : label);
        }

        return () -> columns;
    }

    /**
     * Returns what a logger without explicit parameters logs besides the posterior, prior and
     * likelihood: the statistics of every tree, all sampled parameters and all deterministic values.
     */
    private List<Loggable> getDefaultLoggedLoggables(BeastXState beastState) {
        List<Loggable> loggables =
                new ArrayList<>();

        for (String treeName : beastState.treeModelsByPhyloSpecName.keySet()) {
            TreeModel treeModel =
                    beastState.treeModelsByPhyloSpecName.get(treeName);

            loggables.add(TreeStatisticsLoggable.all(treeModel, treeName));
        }

        loggables.addAll(beastState.stateNodes.keySet());
        loggables.addAll(beastState.calculationNodes.keySet());

        return loggables;
    }

    private Loggable findLoggable(BeastXState beastState, String loggableName) {
        TreeModel treeModel =
                beastState.treeModelsByPhyloSpecName.get(loggableName);

        if (treeModel != null) {
            return TreeStatisticsLoggable.all(treeModel, loggableName);
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

        if (statistic != null && statistic.getDimension() == 1) {
            // Log it under the PhyloSpec name, as the XML logger does, not the statistic's own name.
            return () -> new LogColumn[]{
                    new NumberColumn(loggableName) {
                        @Override
                        public double getDoubleValue() {
                            return statistic.getStatisticValue(0);
                        }
                    }
            };
        }

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
