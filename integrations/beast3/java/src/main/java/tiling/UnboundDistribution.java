package tiling;

import java.util.function.Consumer;

/// A distribution paired with a setter that wires its state node.
///
/// The distribution is "unbound" in the sense that it holds no default state node —
/// binding must always be triggered with an explicit observed state node.
public class UnboundDistribution<T, O extends beast.base.inference.Distribution> {

    public final O distribution;
    protected Consumer<T> setStateNodeFunc;

    /**
     * Constructs an unbound distribution wrapping the given BEAST distribution and
     * the setter used to attach a state node to it.
     */
    public UnboundDistribution(O distribution, Consumer<T> setStateNodeFunc) {
        this.distribution = distribution;
        this.setStateNodeFunc = setStateNodeFunc;
    }

    /**
     * Wires the given observed state node into the distribution via the setter.
     */
    public void bind(Object observedStateNode) {
        this.setStateNodeFunc.accept((T) observedStateNode);
    }

}
