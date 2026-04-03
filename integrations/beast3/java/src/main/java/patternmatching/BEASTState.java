package patternmatching;

import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class BEASTState {

    private final Set<StateNode> stateNodes;
    private final HashMap<StateNode, Distribution> distributions;
    private final Set<Operator> operators;

    public BEASTState() {
        this.stateNodes = new HashSet<>();
        this.distributions = new HashMap<>();
        this.operators = new HashSet<>();
    }

    public void addStateNode(StateNode stateNode) {
        this.stateNodes.add(stateNode);
    }

    public void addDistribution(StateNode stateNode, Distribution distribution) {
        this.distributions.put(stateNode, distribution);
    }

    public void addOperator(Operator operator) {
        this.operators.add(operator);
    }

    public void replaceDistribution(StateNode stateNode, Distribution distribution) {
        this.distributions.put(stateNode, distribution);
    }

}
