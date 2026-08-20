package beastconfig;

import beast.base.core.BEASTObject;
import beast.base.core.Loggable;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeStatLogger;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Logger;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;

import java.util.ArrayList;
import java.util.List;

/// Selects and adds logger specs and loggers to the BEAST state based on the available loggable objects.
///
/// If no screen or file loggers are present, default loggers are created that log all loggable
/// objects (state nodes and calculation nodes). If no tree loggers are present, a default tree
/// logger is created for all trees in the state. Every screen and file logger always logs the
/// posterior, prior and likelihood.
public class LoggerSelector {

    /**
     * Adds the missing loggers specs.
     */
    public static void addMissingLoggerSpecs(BEASTState beastState) {
        List<BEASTObject> loggableObjects = getLoggableObjects(beastState);

        if (beastState.screenLoggerSpecs.isEmpty()) {
            beastState.addScreenLoggerSpec(new ScreenLoggerSpec<>(1000, loggableObjects));
        }
        if (beastState.fileLoggerSpecs.isEmpty()) {
            beastState.addFileLoggerSpec(new FileLoggerSpec<>(1000, beastState.runName + ".log", loggableObjects));
        }

        List<Tree> loggableTrees = getLoggableTrees(beastState);

        if (!loggableTrees.isEmpty() && beastState.treeLoggerSpecs.isEmpty()) {
            for (Tree tree : loggableTrees) {
                String name = loggableTrees.size() == 1 ? "" : "-" + tree.getID();
                beastState.addTreeLoggerSpec(new TreeLoggerSpec<>(
                        1000, beastState.runName + name + ".trees", tree
                ));
            }
        }
    }

    /**
     * Builds a BEAST screen logger according to the given specs.
     */
    public static Logger buildScreenLogger(
            ScreenLoggerSpec<BEASTObject> spec, BEASTState beastState,
            CompoundDistribution posterior, CompoundDistribution prior, CompoundDistribution likelihood
    ) {
        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, spec.logEvery());
        beastState.setInput(logger, logger.sortModeInput, Logger.SORTMODE.smart);
        beastState.setInput(logger, logger.sanitiseHeadersInput, true);

        List<BEASTObject> parameters = spec.parameters() != null ? spec.parameters() : getLoggableObjects(beastState);
        beastState.setInput(logger, logger.loggersInput, withDistributions(parameters, posterior, prior, likelihood));

        return logger;
    }

    /**
     * Builds a BEAST file logger according to the given specs.
     */
    public static Logger buildFileLogger(
            FileLoggerSpec<BEASTObject> spec, BEASTState beastState,
            CompoundDistribution posterior, CompoundDistribution prior, CompoundDistribution likelihood
    ) {
        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, spec.logEvery());
        beastState.setInput(logger, logger.fileNameInput, spec.fileName());
        beastState.setInput(logger, logger.sortModeInput, Logger.SORTMODE.smart);
        beastState.setInput(logger, logger.sanitiseHeadersInput, true);

        List<BEASTObject> parameters = spec.parameters() != null ? spec.parameters() : getLoggableObjects(beastState);
        beastState.setInput(logger, logger.loggersInput, withDistributions(parameters, posterior, prior, likelihood));

        return logger;
    }

    /**
     * Builds a BEAST tree logger according to the given specs.
     * If the spec does not name a tree, all loggable trees in the state are logged.
     */
    public static Logger buildTreeLogger(TreeLoggerSpec<Tree> spec, BEASTState beastState) {
        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, spec.logEvery());
        beastState.setInput(logger, logger.fileNameInput, spec.fileName());
        beastState.setInput(logger, logger.modeInput, beast.base.inference.Logger.LOGMODE.tree);

        List<BEASTObject> trees =
                spec.tree() != null ? List.of(spec.tree()) : new ArrayList<>(getLoggableTrees(beastState));

        if (trees.isEmpty()) {
            throw new RuntimeException("A custom tree logger is provided, but the model does not seem to contain a tree.");
        } else if (1 < trees.size()) {
            throw new RuntimeException("A custom tree logger is provided, but the model has more than one tree. Specify the tree to log with 'tree=<treeName>'");
        }

        beastState.setInput(logger, logger.loggersInput, trees);

        return logger;
    }

    /**
     * Returns the given loggables prefixed with the posterior, prior and likelihood.
     * Loggables that are already among these distributions are not added twice.
     */
    private static List<BEASTObject> withDistributions(
            List<BEASTObject> loggables,
            CompoundDistribution posterior, CompoundDistribution prior, CompoundDistribution likelihood
    ) {
        List<BEASTObject> combined = new ArrayList<>(List.of(posterior, prior, likelihood));

        for (BEASTObject loggable : loggables) {
            if (!combined.contains(loggable)) {
                combined.add(loggable);
            }
        }

        return combined;
    }

    /**
     * Returns all state nodes which can be logged by a screen or file logger.
     */
    private static List<BEASTObject> getLoggableObjects(BEASTState beastState) {
        List<BEASTObject> loggables = new ArrayList<>();

        for (BEASTObject object : beastState.stateNodes.keySet()) {
            if (object.getID() != null && object instanceof Loggable && !(object instanceof Tree)) {
                loggables.add(object);
            }

            if (object instanceof Tree tree) {
                TreeStatLogger treeStatLogger = new TreeStatLogger();
                beastState.setInput(treeStatLogger, treeStatLogger.treeInput, tree);
                loggables.add(treeStatLogger);
            }
        }
        for (BEASTObject object : beastState.calculationNodes.keySet()) {
            if (object.getID() != null && object instanceof Loggable && !(object instanceof Tree)) {
                loggables.add(object);
            }
        }

        return loggables;
    }

    /**
     * Returns all state nodes which can be logged by a tree logger.
     */
    private static List<Tree> getLoggableTrees(BEASTState beastState) {
        List<Tree> loggables = new ArrayList<>();

        for (BEASTObject object : beastState.stateNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree tree) {
                loggables.add(tree);
            }
        }
        for (BEASTObject object : beastState.calculationNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree tree) {
                loggables.add(tree);
            }
        }

        return loggables;
    }

}
