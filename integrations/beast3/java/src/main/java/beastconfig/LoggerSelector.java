package beastconfig;

import beast.base.core.BEASTObject;
import beast.base.core.Loggable;
import beast.base.evolution.tree.Tree;

import java.util.ArrayList;
import java.util.List;

public class LoggerSelector {

    /**
     * Adds the missing loggers to the beast state.
     */
    public static void addMissingLoggers(BEASTState beastState) {
        List<BEASTObject> loggableObjects = getLoggableObjects(beastState);

        if (!loggableObjects.isEmpty() && beastState.screenLoggers.isEmpty()) {
            beast.base.inference.Logger screenLogger = new beast.base.inference.Logger();
            beastState.setInput(screenLogger, screenLogger.everyInput, 1000);
            beastState.setInput(screenLogger, screenLogger.loggersInput, loggableObjects);
            beastState.addScreenLogger(screenLogger);
        }
        if (!loggableObjects.isEmpty() && beastState.fileLoggers.isEmpty()) {
            beast.base.inference.Logger fileLogger = new beast.base.inference.Logger();
            beastState.setInput(fileLogger, fileLogger.fileNameInput, beastState.runName + ".log");
            beastState.setInput(fileLogger, fileLogger.everyInput, 1000);
            beastState.setInput(fileLogger, fileLogger.loggersInput, loggableObjects);
            beastState.addScreenLogger(fileLogger);
        }

        List<BEASTObject> loggableTrees = getLoggableTrees(beastState);

        if (!loggableTrees.isEmpty() && beastState.treeLoggers.isEmpty()) {
            beast.base.inference.Logger treeLogger = new beast.base.inference.Logger();
            beastState.setInput(treeLogger, treeLogger.modeInput, beast.base.inference.Logger.LOGMODE.tree);
            beastState.setInput(treeLogger, treeLogger.fileNameInput, beastState.runName + ".trees");
            beastState.setInput(treeLogger, treeLogger.everyInput, 1000);
            beastState.setInput(treeLogger, treeLogger.loggersInput, loggableTrees);
            beastState.addScreenLogger(treeLogger);
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

        return loggables;
    }

}
