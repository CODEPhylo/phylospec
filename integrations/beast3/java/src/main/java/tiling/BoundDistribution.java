package tiling;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import org.phylospec.tiling.TypeToken;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

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
    private final BiFunction<T, BEASTState, List<Operator>> operatorFactory;

    /**
     * Constructs a bound distribution with the given BEAST distribution, default state node,
     * the setter used to attach a state node to the distribution, and the function returning
     * the operators to register for the default state node once it is bound.
     */
    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc, BiFunction<T, BEASTState, List<Operator>> operatorFactory) {
        super(distribution, setStateNodeFunc);
        this.stateNode = defaultState;
        this.operatorFactory = operatorFactory;
    }

    /**
     * Constructs a bound distribution with the given BEAST distribution, default state node,
     * the setter used to attach a state node to the distribution, and the function returning
     * the operators to register for the default state node once it is bound.
     */
    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc) {
        this(distribution, defaultState, setStateNodeFunc, OperatorSelector::getDefaultOperators);
    }

    /**
     * Wires the stored default state node into the distribution, registers it (with the
     * given id) as a state node and its distribution as a prior in the given BEAST state,
     * and registers the operators its operator function returns. Returns the resulting state node.
     */
    public T draw(BEASTState beastState, TypeToken<?> typeToken, String stateId) {
        this.markAsConsumed();
        this.setStateNodeFunc.accept(this.stateNode);
        beastState.addStateNode(this.stateNode, typeToken, stateId);
        beastState.addPriorDistribution(this.stateNode, this.distribution, stateId + "_prior");
        beastState.addOperators(this.stateNode, this.operatorFactory.apply(this.stateNode, beastState));
        return this.stateNode;
    }

    public T getStateNode() {
        return this.stateNode;
    }

}
