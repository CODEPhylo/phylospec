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
    public final HashMap<Operator, Set<StateNode>> operators;
    private final List<BEASTObject> beastObjects;

    private final Set<String> ids;

    public BEASTState() {
        this.stateNodes = new HashMap<>();
        this.distributions = new HashMap<>();
        this.operators = new HashMap<>();
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

        try {
            beastObject.initAndValidate();
        } catch (Exception e) {
            // we cannot initiate it so far
        }

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

    public void initializeBEASTObjects() {
       for (int i = 0; i < this.beastObjects.size(); i++) {
            BEASTObject beastObject = this.beastObjects.get(i);
            // if (i != lastOccurrences.get(beastObject)) continue;

            beastObject.determindClassOfInputs();
            beastObject.validateInputs();
            try {
                beastObject.initAndValidate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (BEASTObject beastObject : this.beastObjects) {
            if (beastObject instanceof StateNodeInitialiser stateNodeInitialiser) {
                stateNodeInitialiser.initStateNodes();
            }
        }
    }

}
