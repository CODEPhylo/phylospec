package tiling;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeToken;

import java.util.List;
import java.util.function.Consumer;

/// A distribution with a default state node that can be wired without an external observed value.
///
/// Extends {@link UnboundDistribution} by storing a concrete default state node.
/// Binding can be triggered with the stored default or overridden with an observed state node.
public class BoundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> extends UnboundDistribution<T, O> {

    public T stateNode;

    /// Operators to register for the default state node once it is bound, or {@code null}
    /// if the tile that created this distribution didn't choose any, leaving the default
    /// state node to fall back to {@link beastconfig.OperatorSelector}'s type-based defaults.
    public List<Operator> operators;

    /**
     * Constructs a bound distribution with the given BEAST distribution, default state node,
     * the setter used to attach a state node to the distribution, and the operators to
     * register for the default state node once it is bound.
     */
    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc, List<Operator> operators) {
        super(distribution, setStateNodeFunc);
        this.stateNode = defaultState;
        this.operators = operators;
    }

    /**
     * Wires the stored default state node into the distribution, registers it (with the
     * given id) as a state node and its distribution as a prior in the given BEAST state,
     * and registers its operators, if any were chosen. Returns the resulting state node.
     */
    public T bindAndRegisterAsPrior(BEASTState beastState, TypeToken<?> typeToken, String id) {
        this.setStateNodeFunc.accept(this.stateNode);
        beastState.addStateNode(this.stateNode, typeToken, id);
        beastState.addPriorDistribution(this.stateNode, this.distribution, id + "_prior");
        if (this.operators != null) {
            beastState.addOperators(this.stateNode, this.operators);
        }
        return this.stateNode;
    }

    /**
     * Wires the given observed state node into the distribution and updates the stored reference.
     */
    @Override
    public void bind(Object observedStateNode) {
        this.setStateNodeFunc.accept((T) observedStateNode);
        this.stateNode = (T) observedStateNode;
    }

}
