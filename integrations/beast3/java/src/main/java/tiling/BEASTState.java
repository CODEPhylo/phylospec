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

    public BEASTState() {
        this.stateNodes = new HashMap<>();
        this.distributions = new HashMap<>();
        this.operators = new HashSet<>();
        this.beastObjects = new ArrayList<>();
    }

    private BEASTObject addBEASTObject(Object object) {
        BEASTObject beastObject = BEASTObjectStore.INSTANCE.getBEASTObject(object);
        this.beastObjects.add(beastObject);
        return beastObject;
    }

    public <T> void setInput(BEASTObject beastObject, Input<T> input, T value) {
        input.setValue(value, beastObject);

        // add beast object to outputs of input
        this.addBEASTObject(beastObject);
        this.addBEASTObject(value).getOutputs().add(beastObject);
    }

    public void addStateNode(StateNode stateNode, TypeToken<?> typeToken) {
        this.addBEASTObject(stateNode);
        this.stateNodes.put(stateNode, typeToken);
    }

    public void addDistribution(StateNode stateNode, Distribution distribution) {
        this.addBEASTObject(stateNode);
        this.addBEASTObject(distribution);
        this.distributions.put(stateNode, distribution);
    }

    public void addOperator(Operator operator) {
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
