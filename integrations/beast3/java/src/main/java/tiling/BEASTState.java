package tiling;

import beast.base.core.*;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beast.base.inference.StateNodeInitialiser;

import java.util.*;

public class BEASTState {

    public final HashMap<StateNode, TypeToken<?>> stateNodes;
    public final HashMap<StateNode, Distribution> distributions;
    public final Set<Operator> operators;
    private final List<BEASTObject> beastObjects;

    private final Set<String> ids;

    public BEASTState() {
        this.stateNodes = new HashMap<>();
        this.distributions = new HashMap<>();
        this.operators = new HashSet<>();
        this.beastObjects = new ArrayList<>();
        this.ids = new HashSet<>();
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

    private BEASTObject addBEASTObject(Object object) {
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
        this.addBEASTObject(beastObject);
        this.addBEASTObject(value).getOutputs().add(beastObject);
    }

    public void addStateNode(StateNode stateNode, TypeToken<?> typeToken, String id) {
        stateNode.setID(this.getID(id));
        this.addBEASTObject(stateNode);
        this.stateNodes.put(stateNode, typeToken);
    }

    public void addDistribution(StateNode stateNode, Distribution distribution) {
        this.addBEASTObject(stateNode);
        this.addBEASTObject(distribution);
        this.distributions.put(stateNode, distribution);
    }

    public void addOperator(Operator operator, StateNode stateNode) {
        // set id
        if (operator.getID() == null) {
            operator.setID(this.getID(stateNode.getID() + "_operator"));
        }

        this.addBEASTObject(operator);
        this.operators.add(operator);
    }

    public void initializeBEASTObjects() {
        for (BEASTObject beastObject : this.beastObjects) {
            beastObject.determindClassOfInputs();
            beastObject.validateInputs();
            beastObject.initAndValidate();
        }

        for (BEASTObject beastObject : this.beastObjects) {
            if (beastObject instanceof StateNodeInitialiser stateNodeInitialiser) {
                stateNodeInitialiser.initStateNodes();
            }
        }
    }

}
