package beastconfig;

import beast.base.core.*;
import beast.base.evolution.tree.Tree;
import beast.base.inference.*;
import java.util.*;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.mcmc.FileLoggerSpec;
import org.phylospec.tiling.mcmc.ScreenLoggerSpec;
import org.phylospec.tiling.mcmc.TreeLoggerSpec;

/// Manages the BEAST state that is built up incrementally during tiling.
///
/// Tracks state nodes, calculation nodes, distributions, operators, and loggers.
/// Provides utilities for assigning unique IDs, wiring inputs, and initializing
/// BEAST objects in the correct order.
public class BEASTState {

    public final String runName;
    public long chainLength = 10_00_000;

    private final List<BEASTObject> beastObjects;
    private final Set<BEASTObject> initializedBeastObjects;
    private final Set<String> ids;

    public final HashMap<StateNode, TypeToken<?>> stateNodes;
    public final HashMap<CalculationNode, TypeToken<?>> calculationNodes;

    public final HashMap<StateNode, Distribution> priorDistributions;
    public final List<Distribution> likelihoodDistributions;

    public final List<Operator> operators;

    public final List<ScreenLoggerSpec<BEASTObject>> screenLoggerSpecs;
    public final List<FileLoggerSpec<BEASTObject>> fileLoggerSpecs;
    public final List<TreeLoggerSpec<Tree>> treeLoggerSpecs;

    /// The loggers built from the specs, cached so that they are only built once.
    private List<beast.base.inference.Logger> builtLoggers;

    /**
     * Creates a new BEAST state with the given run name.
     */
    public BEASTState(String runName) {
        this.runName = runName;
        this.stateNodes = new HashMap<>();
        this.calculationNodes = new HashMap<>();
        this.priorDistributions = new HashMap<>();
        this.likelihoodDistributions = new ArrayList<>();
        this.operators = new ArrayList<>();
        this.beastObjects = new ArrayList<>();
        this.ids = new HashSet<>();
        this.initializedBeastObjects = new HashSet<>();
        this.screenLoggerSpecs = new ArrayList<>();
        this.fileLoggerSpecs = new ArrayList<>();
        this.treeLoggerSpecs = new ArrayList<>();
    }

    /**
     * Gets the next available ID based on the proposed ID.
     */
    public String getAvailableID(String proposal) {
        if (!this.ids.contains(proposal)) {
            this.ids.add(proposal);
            return proposal;
        }

        int prefix = 2;
        while (this.ids.contains(proposal + "_" + prefix)) {
            prefix++;
        }

        proposal = proposal + "_" + prefix;
        this.ids.add(proposal);
        return proposal;
    }

    /**
     * Adds a new object to the BEAST object store and wraps it into a VirtualBEASTObject if necessary.
     */
    private BEASTObject addBEASTObject(Object object) {
        BEASTObject beastObject = BEASTObjectStore.INSTANCE.getBEASTObject(object);
        this.beastObjects.add(beastObject);
        return beastObject;
    }

    /**
     * Initializes a BEAST object.
     */
    public void initBEASTObject(Object object) {
        BEASTObject beastObject = BEASTObjectStore.INSTANCE.getBEASTObject(object);

        if (this.initializedBeastObjects.contains(beastObject)) return;

        beastObject.determindClassOfInputs();
        beastObject.validateInputs();
        beastObject.initAndValidate();
        this.initializedBeastObjects.add(beastObject);
    }

    /**
     * Sets the given input of the beast object to the value.
     * If value does not have an ID yet, it is tried to construct one based on the beastObject ID if possible.
     * Furthermore, value is initialized.
     */
    public <T> void setInput(BEASTObject beastObject, Input<T> input, T value) {
        input.setValue(value, beastObject);

        // set id
        if (beastObject.getID() != null && value instanceof BEASTObject beastValue && beastValue.getID() == null) {
            String valueId = beastObject.getID() + "_" + input.getName();
            beastValue.setID(this.getAvailableID(valueId));
        }

        // add beast object to outputs of input
        this.addBEASTObject(value).getOutputs().add(beastObject);
        this.addBEASTObject(beastObject);

        // initialize the input
        this.initBEASTObject(value);
    }

    /**
     * Adds a given state node to the BEAST state without registering any operators for it. The
     * caller is responsible for adding operators via {@link #addOperators}.
     */
    public void addStateNodeWithoutOperators(StateNode stateNode, TypeToken<?> typeToken, String id) {
        stateNode.setID(this.getAvailableID(id));
        this.addBEASTObject(stateNode);
        this.stateNodes.put(stateNode, typeToken);
    }

    /**
     * Adds a given calculation node to the BEAST state.
     */
    public void addCalculationNode(CalculationNode calculationNode, TypeToken<?> typeToken, String id) {
        calculationNode.setID(this.getAvailableID(id));
        this.addBEASTObject(calculationNode);
        this.calculationNodes.put(calculationNode, typeToken);
    }

    /**
     * Adds a given prior distribution to the BEAST state.
     */
    public void addPriorDistribution(StateNode stateNode, Distribution distribution, String id) {
        distribution.setID(this.getAvailableID(id));
        this.addBEASTObject(stateNode);
        this.addBEASTObject(distribution);
        this.priorDistributions.put(stateNode, distribution);
    }

    /**
     * Adds a given likelihood to the BEAST state.
     */
    public void addLikelihoodDistribution(Distribution distribution, String id) {
        distribution.setID(this.getAvailableID(id));
        this.addBEASTObject(distribution);
        this.likelihoodDistributions.add(distribution);
    }

    /**
     * Adds all given operators to the BEAST state, naming each of them after the given state node.
     */
    public void addOperators(StateNode stateNode, List<Operator> operators) {
        this.addOperators(List.of(stateNode), operators);
    }

    /**
     * Adds all given operators to the BEAST state, naming each of them after the given state nodes.
     */
    public void addOperators(List<StateNode> stateNodes, List<Operator> operators) {
        for (Operator operator : operators) {
            this.addOperator(operator, stateNodes);
        }
    }

    /**
     * Adds a given operator to the BEAST state, naming it after the given state node if it has no
     * ID yet.
     */
    public void addOperator(Operator operator, StateNode stateNode) {
        this.addOperator(operator, List.of(stateNode));
    }

    /**
     * Adds a given operator to the BEAST state, naming it after the given state nodes if it has no
     * ID yet.
     */
    public void addOperator(Operator operator, List<StateNode> stateNodes) {
        // set id
        if (operator.getID() == null) {
            StringBuilder id = new StringBuilder();
            for (StateNode stateNode : stateNodes) {
                id.append(stateNode.getID()).append("_");
            }
            id.append("operator");
            operator.setID(this.getAvailableID(id.toString()));
        }

        this.addBEASTObject(operator);
        this.operators.add(operator);
    }

    /**
     * Adds a given screen logger to the BEAST state.
     */
    public void addScreenLoggerSpec(ScreenLoggerSpec<BEASTObject> logger) {
        this.screenLoggerSpecs.add(logger);
    }

    /**
     * Adds a given file logger to the BEAST state.
     */
    public void addFileLoggerSpec(FileLoggerSpec<BEASTObject> logger) {
        this.fileLoggerSpecs.add(logger);
    }

    /**
     * Adds a given tree logger to the BEAST state.
     */
    public void addTreeLoggerSpec(TreeLoggerSpec<Tree> logger) {
        this.treeLoggerSpecs.add(logger);
    }

    /**
     * Constructs loggers from the registered logger specs, filling in the default loggers that
     * the specs do not already cover. The loggers are built once and cached, so repeated calls
     * return the same logger instances rather than duplicates.
     */
    public List<beast.base.inference.Logger> buildLoggers(
            CompoundDistribution posterior, CompoundDistribution prior, CompoundDistribution likelihood) {
        if (this.builtLoggers != null) {
            return this.builtLoggers;
        }

        // add missing loggers

        LoggerSelector.addMissingLoggerSpecs(this);

        // build loggers from specs

        List<beast.base.inference.Logger> loggers = new ArrayList<>();

        for (ScreenLoggerSpec<BEASTObject> spec : this.screenLoggerSpecs) {
            loggers.add(LoggerSelector.buildScreenLogger(spec, this, posterior, prior, likelihood));
        }

        for (FileLoggerSpec<BEASTObject> spec : this.fileLoggerSpecs) {
            loggers.add(LoggerSelector.buildFileLogger(spec, this, posterior, prior, likelihood));
        }

        for (TreeLoggerSpec<Tree> spec : this.treeLoggerSpecs) {
            loggers.add(LoggerSelector.buildTreeLogger(spec, this));
        }

        this.builtLoggers = loggers;

        return loggers;
    }

    /**
     * Initialized all BEAST objects that have not yet been registered.
     */
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
