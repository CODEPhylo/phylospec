package beastconfig;

import beast.base.core.BEASTObject;
import beast.base.core.Loggable;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeStatLogger;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Logger;
import java.util.ArrayList;
import java.util.List;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.LoggerSpec;
import org.phylospec.tiling.mcmc.OutputPaths;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;
import tiling.RenamedLoggable;

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
            beastState.addScreenLoggerSpec(new ScreenLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, loggableObjects, null));
        }
        if (beastState.fileLoggerSpecs.isEmpty()) {
            beastState.addFileLoggerSpec(new FileLoggerSpec<>(
                    LoggerSpec.DEFAULT_LOG_EVERY,
                    OutputPaths.getLogFile(beastState.outputPrefix),
                    loggableObjects,
                    null));
        }

        List<Tree> loggableTrees = getLoggableTrees(beastState);

        if (!loggableTrees.isEmpty() && beastState.treeLoggerSpecs.isEmpty()) {
            for (Tree tree : loggableTrees) {
                String fileName = loggableTrees.size() == 1
                        ? OutputPaths.getTreeFile(beastState.outputPrefix)
                        : OutputPaths.getTreeFile(beastState.outputPrefix, tree.getID());
                beastState.addTreeLoggerSpec(new TreeLoggerSpec<>(LoggerSpec.DEFAULT_LOG_EVERY, fileName, tree));
            }
        }
    }

    /**
     * Builds a BEAST screen logger according to the given specs.
     */
    public static Logger buildScreenLogger(
            ScreenLoggerSpec<BEASTObject> spec,
            BEASTState beastState,
            CompoundDistribution posterior,
            CompoundDistribution prior,
            CompoundDistribution likelihood) {
        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, spec.logEvery());
        beastState.setInput(logger, logger.sortModeInput, Logger.SORTMODE.smart);
        beastState.setInput(logger, logger.sanitiseHeadersInput, true);

        List<BEASTObject> parameters = getLoggedParameters(spec.parameters(), spec.parameterNames(), beastState);
        beastState.setInput(logger, logger.loggersInput, withDistributions(parameters, posterior, prior, likelihood));

        return logger;
    }

    /**
     * Builds a BEAST file logger according to the given specs.
     */
    public static Logger buildFileLogger(
            FileLoggerSpec<BEASTObject> spec,
            BEASTState beastState,
            CompoundDistribution posterior,
            CompoundDistribution prior,
            CompoundDistribution likelihood) {
        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, spec.logEvery());
        beastState.setInput(logger, logger.fileNameInput, beastState.resolveOutputPath(spec.fileName()));
        beastState.setInput(logger, logger.sortModeInput, Logger.SORTMODE.smart);
        beastState.setInput(logger, logger.sanitiseHeadersInput, true);

        List<BEASTObject> parameters = getLoggedParameters(spec.parameters(), spec.parameterNames(), beastState);
        beastState.setInput(logger, logger.loggersInput, withDistributions(parameters, posterior, prior, likelihood));

        return logger;
    }

    /**
     * Builds a BEAST tree logger according to the given specs.
     */
    public static Logger buildTreeLogger(TreeLoggerSpec<Tree> spec, BEASTState beastState) {
        Logger logger = new Logger();
        beastState.setInput(logger, logger.everyInput, spec.logEvery());
        beastState.setInput(logger, logger.fileNameInput, beastState.resolveOutputPath(spec.fileName()));
        beastState.setInput(logger, logger.modeInput, beast.base.inference.Logger.LOGMODE.tree);

        beastState.setInput(logger, logger.loggersInput, new ArrayList<BEASTObject>(List.of(spec.tree())));

        return logger;
    }

    /**
     * Returns the objects a screen or file logger logs for the given parameters. Without parameters,
     * these are all loggable objects of the state. Parameters with a name are logged under that name.
     */
    private static List<BEASTObject> getLoggedParameters(
            List<BEASTObject> parameters, List<String> parameterNames, BEASTState beastState) {
        if (parameters == null) return getLoggableObjects(beastState);
        if (parameterNames == null) return parameters;

        List<BEASTObject> renamedParameters = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            RenamedLoggable renamedParameter = new RenamedLoggable();
            beastState.setInput(renamedParameter, renamedParameter.loggableInput, parameters.get(i));
            beastState.setInput(renamedParameter, renamedParameter.labelInput, parameterNames.get(i));
            renamedParameters.add(renamedParameter);
        }

        return renamedParameters;
    }

    /**
     * Returns the given loggables prefixed with the posterior, prior and likelihood.
     * Loggables that are already among these distributions are not added twice.
     */
    private static List<BEASTObject> withDistributions(
            List<BEASTObject> loggables,
            CompoundDistribution posterior,
            CompoundDistribution prior,
            CompoundDistribution likelihood) {
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
