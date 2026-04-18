package beastconfig;

import beast.base.core.BEASTObject;
import beast.base.core.Loggable;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeStatLogger;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Logger;

import java.util.ArrayList;
import java.util.List;

/// Selects and adds loggers to the BEAST state based on the available loggable objects.
///
/// If no screen or file loggers are present, default loggers are created that log all loggable
/// objects (state nodes and calculation nodes). If no tree loggers are present, a default tree
/// logger is created for all trees in the state.
public class LoggerSelector {

    /**
     * Adds the missing loggers to the beast state.
     */
    public static void addMissingLoggers(
            BEASTState beastState, CompoundDistribution posterior, CompoundDistribution prior, CompoundDistribution likelihood
    ) {
        List<BEASTObject> loggableObjects = getLoggableObjects(beastState);
        loggableObjects.add(posterior);
        loggableObjects.add(prior);
        loggableObjects.add(likelihood);

        if (beastState.screenLoggers.isEmpty()) {
            beast.base.inference.Logger screenLogger = new beast.base.inference.Logger();
            beastState.setInput(screenLogger, screenLogger.everyInput, 1000);
            beastState.setInput(screenLogger, screenLogger.loggersInput, loggableObjects);
            beastState.setInput(screenLogger, screenLogger.sortModeInput, Logger.SORTMODE.smart);
            beastState.setInput(screenLogger, screenLogger.sanitiseHeadersInput, true);
            beastState.addScreenLogger(screenLogger);
        }
        if (beastState.fileLoggers.isEmpty()) {
            beast.base.inference.Logger fileLogger = new beast.base.inference.Logger();
            beastState.setInput(fileLogger, fileLogger.fileNameInput, beastState.runName + ".log");
            beastState.setInput(fileLogger, fileLogger.everyInput, 1000);
            beastState.setInput(fileLogger, fileLogger.loggersInput, loggableObjects);
            beastState.addFileLogger(fileLogger);
        }

        List<BEASTObject> loggableTrees = getLoggableTrees(beastState);

        if (!loggableTrees.isEmpty() && beastState.treeLoggers.isEmpty()) {
            for (BEASTObject tree : loggableTrees) {
                beast.base.inference.Logger treeLogger = new beast.base.inference.Logger();

                String name = loggableTrees.size() == 1 ? "" : "-" + tree.getID();
                beastState.setInput(treeLogger, treeLogger.fileNameInput, beastState.runName + name + ".trees");

                beastState.setInput(treeLogger, treeLogger.modeInput, beast.base.inference.Logger.LOGMODE.tree);
                beastState.setInput(treeLogger, treeLogger.everyInput, 1000);
                beastState.setInput(treeLogger, treeLogger.loggersInput, List.of(tree));

                beastState.addTreeLogger(treeLogger);
            }
        }
    }

    /**
     * Returns all state nodes which can be logged by a screen or file logger.
     */
    public static List<BEASTObject> getLoggableObjects(BEASTState beastState) {
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
    public static List<BEASTObject> getLoggableTrees(BEASTState beastState) {
        List<BEASTObject> loggables = new ArrayList<>();

        for (BEASTObject object : beastState.stateNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree) {
                loggables.add(object);
            }
        }
        for (BEASTObject object : beastState.calculationNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree) {
                loggables.add(object);
            }
        }

        return loggables;
    }

}
