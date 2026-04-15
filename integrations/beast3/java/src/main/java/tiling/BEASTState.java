package tiling;

import beast.base.core.*;
import beast.base.evolution.tree.Tree;
import beast.base.inference.*;

import java.util.*;

public class BEASTState {

    private final String runName;

    public final HashMap<StateNode, TypeToken<?>> stateNodes;
    public final HashMap<StateNode, Distribution> distributions;
    public final HashMap<Operator, Set<StateNode>> operators;
    private final List<BEASTObject> beastObjects;
    private final Set<BEASTObject> initializedBeastObjects;

    public long chainLength = 10_00_000;
    public final List<Logger> screenLoggers;
    public final List<Logger> fileLoggers;
    public final List<Logger> treeLoggers;

    private final Set<String> ids;

    public BEASTState(String runName) {
        this.runName = runName;
        this.stateNodes = new HashMap<>();
        this.distributions = new HashMap<>();
        this.operators = new HashMap<>();
        this.beastObjects = new ArrayList<>();
        this.ids = new HashSet<>();
        this.initializedBeastObjects = new HashSet<>();
        this.screenLoggers = new ArrayList<>();
        this.fileLoggers = new ArrayList<>();
        this.treeLoggers = new ArrayList<>();
    }

    public String getID(String id) {
        if (!this.ids.contains(id)) {
            this.ids.add(id);
            return id;
        }

        int prefix = 1;
        while (this.ids.contains(id + prefix)) {
            prefix++;
        }

        id = id + prefix;
        this.ids.add(id);
        return id;
    }

    public void initBEASTObject(Object object) {
        BEASTObject beastObject = BEASTObjectStore.INSTANCE.getBEASTObject(object);

        if (this.initializedBeastObjects.contains(beastObject)) return;

        beastObject.determindClassOfInputs();
        beastObject.validateInputs();
        beastObject.initAndValidate();
        this.initializedBeastObjects.add(beastObject);
    }

    public BEASTObject addBEASTObject(Object object) {
        BEASTObject beastObject = BEASTObjectStore.INSTANCE.getBEASTObject(object);
        this.beastObjects.add(beastObject);
        return beastObject;
    }

    public <T> void setInput(BEASTObject beastObject, Input<T> input, T value) {
        input.setValue(value, beastObject);

        // set id
        if (beastObject.getID() != null && value instanceof BEASTObject beastValue && beastValue.getID() == null) {
            String valueId = beastObject.getID() + "_" + input.getName();
            beastValue.setID(this.getID(valueId));
        }

        // add beast object to outputs of input
        this.addBEASTObject(value).getOutputs().add(beastObject);
        this.addBEASTObject(beastObject);

        // initialize the input
        this.initBEASTObject(value);
    }

    public void addStateNode(StateNode stateNode, TypeToken<?> typeToken, String id) {
        stateNode.setID(this.getID(id));
        this.addBEASTObject(stateNode);
        this.stateNodes.put(stateNode, typeToken);
    }

    public void addDistribution(StateNode stateNode, Distribution distribution, String id) {
        distribution.setID(this.getID(id));
        this.addBEASTObject(stateNode);
        this.addBEASTObject(distribution);
        this.distributions.put(stateNode, distribution);
    }

    public void addOperator(Operator operator, StateNode stateNode) {
        this.addOperator(operator, Set.of(stateNode));
    }

    public void addOperator(Operator operator, Set<StateNode> stateNodes) {
        // set id
        if (operator.getID() == null) {
            StringBuilder id = new StringBuilder();
            for (StateNode stateNode : stateNodes) {
                id.append(stateNode.getID()).append("_");
            }
            id.append("operator");
            operator.setID(id.toString());
        }

        this.addBEASTObject(operator);
        this.operators.put(operator, stateNodes);
    }

    public List<BEASTObject> getLoggableObjects() {
        List<BEASTObject> loggables = new ArrayList<>();

        for (BEASTObject object : this.stateNodes.keySet()) {
            if (object.getID() != null && object instanceof Loggable && !(object instanceof Tree)) {
                loggables.add(object);
            }
        }

        return loggables;
    }

    public List<BEASTObject> getLoggableTrees() {
        List<BEASTObject> loggables = new ArrayList<>();

        for (BEASTObject object : this.stateNodes.keySet()) {
            if (object.getID() != null && object instanceof Tree) {
                loggables.add(object);
            }
        }

        return loggables;
    }

    public void addScreenLogger(Logger logger) {
        this.screenLoggers.add(logger);
    }
    public void addFileLogger(Logger logger) {
        this.fileLoggers.add(logger);
    }
    public void addTreeLogger(Logger logger) {
        this.treeLoggers.add(logger);
    }

    public List<Logger> getLoggers() {
        List<Logger> loggers = new ArrayList<>();
        loggers.addAll(this.screenLoggers);
        loggers.addAll(this.fileLoggers);
        loggers.addAll(this.treeLoggers);
        return loggers;
    }

    public void addMissingLoggers() {
        List<BEASTObject> loggableObjects = this.getLoggableObjects();

        if (!loggableObjects.isEmpty() && this.screenLoggers.isEmpty()) {
            Logger screenLogger = new Logger();
            this.setInput(screenLogger, screenLogger.everyInput, 1000);
            this.setInput(screenLogger, screenLogger.loggersInput, loggableObjects);
            this.addScreenLogger(screenLogger);
        }
        if (!loggableObjects.isEmpty() && this.fileLoggers.isEmpty()) {
            Logger fileLogger = new Logger();
            this.setInput(fileLogger, fileLogger.fileNameInput, this.runName + ".log");
            this.setInput(fileLogger, fileLogger.everyInput, 1000);
            this.setInput(fileLogger, fileLogger.loggersInput, loggableObjects);
            this.addScreenLogger(fileLogger);
        }

        List<BEASTObject> loggableTrees = this.getLoggableTrees();

        if (!loggableTrees.isEmpty() && this.treeLoggers.isEmpty()) {
            Logger treeLogger = new Logger();
            this.setInput(treeLogger, treeLogger.modeInput, Logger.LOGMODE.tree);
            this.setInput(treeLogger, treeLogger.fileNameInput, this.runName + ".trees");
            this.setInput(treeLogger, treeLogger.everyInput, 1000);
            this.setInput(treeLogger, treeLogger.loggersInput, loggableTrees);
            this.addScreenLogger(treeLogger);
        }
    }

    public void initializeBEASTObjects() {
        for (BEASTObject object : this.beastObjects) {
            this.initBEASTObject(object);
        }

        for (BEASTObject beastObject : this.beastObjects) {
            if (beastObject instanceof StateNodeInitialiser stateNodeInitialiser) {
                stateNodeInitialiser.initStateNodes();
            }
        }
    }

}
