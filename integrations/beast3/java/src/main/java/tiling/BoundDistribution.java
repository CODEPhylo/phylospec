package tiling;

import beast.base.inference.StateNode;

import java.util.function.Consumer;

/// A distribution with a default state node that can be wired without an external observed value.
///
/// Extends {@link UnboundDistribution} by storing a concrete default state node.
/// Binding can be triggered with the stored default or overridden with an observed state node.
public class BoundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> extends UnboundDistribution<T, O> {

    public T stateNode;

    /**
     * Constructs a bound distribution with the given BEAST distribution, default state node,
     * and the setter used to attach a state node to the distribution.
     */
    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc) {
        super(distribution, setStateNodeFunc);
        this.stateNode = defaultState;
    }

    /**
     * Wires the stored default state node into the distribution.
     */
    public void bind() {
        this.setStateNodeFunc.accept(this.stateNode);
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
