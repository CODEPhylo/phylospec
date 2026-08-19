package tiling;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;

import java.util.function.Consumer;

/// A distribution paired with a setter that wires its state node.
///
/// The distribution is "unbound" in the sense that it holds no default state node —
/// binding must always be triggered with an explicit observed state node.
public class UnboundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> {

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
     * Wires the given observed value into the distribution and registers the distribution
     * as a likelihood in the given BEAST state — the observed-data counterpart of
     * {@link BoundDistribution#bindAndRegisterAsPrior}.
     */
    public void bindAndRegisterAsLikelihood(BEASTState beastState, Object observedValue, String id) {
        this.setStateNodeFunc.accept((T) observedValue);
        beastState.addLikelihoodDistribution(this.distribution, id);
    }

}
