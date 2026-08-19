package tiling;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeToken;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// A distribution with a default state node that can be wired without an external observed value.
///
/// Extends {@link UnboundDistribution} by storing a concrete default state node.
/// Binding can be triggered with the stored default or overridden with an observed state node.
public class BoundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> extends UnboundDistribution<T, O> {

    private final T stateNode;

    /// Returns the operators to register for the default state node, applied to that state
    /// node once it is bound. Deferring the call lets the tile that created this distribution
    /// choose operators based on the fully wired state node, and skips building them
    /// entirely when an observed state node replaces the default.
    private final Function<T, List<Operator>> getOperatorsFunc;

    /**
     * Constructs a bound distribution with the given BEAST distribution, default state node,
     * the setter used to attach a state node to the distribution, and the function returning
     * the operators to register for the default state node once it is bound.
     */
    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc, Function<T, List<Operator>> getOperatorsFunc) {
        super(distribution, setStateNodeFunc);
        this.stateNode = defaultState;
        this.getOperatorsFunc = getOperatorsFunc;
    }

    /**
     * Wires the stored default state node into the distribution, registers it (with the
     * given id) as a state node and its distribution as a prior in the given BEAST state,
     * and registers the operators its operator function returns. Returns the resulting state node.
     */
    public T bindAndRegisterAsPrior(BEASTState beastState, TypeToken<?> typeToken, String id) {
        this.setStateNodeFunc.accept(this.stateNode);
        beastState.addStateNode(this.stateNode, typeToken, id);
        beastState.addPriorDistribution(this.stateNode, this.distribution, id + "_prior");
        beastState.addOperators(this.stateNode, this.getOperatorsFunc.apply(this.stateNode));
        return this.stateNode;
    }

    public T getStateNode() {
        return stateNode;
    }

}
