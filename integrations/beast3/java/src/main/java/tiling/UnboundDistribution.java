package tiling;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import java.util.function.Consumer;

/// A BEAST distribution that is not yet attached to the value it describes, paired with the
/// setter that attaches a value to it.
///
/// Tiles build a distribution before they know what it applies to, because that value only
/// becomes available once the surrounding statement is tiled. This class carries the
/// distribution until then. It is "unbound" in the sense that it carries no candidate value of
/// its own: the value must be supplied from the outside via {@link #observeAs}.
/// The subclass {@link BoundDistribution} adds a default state node and can
/// therefore also be resolved without an external value.
///
/// A distribution may only be resolved once — see {@link #markAsConsumed}.
public class UnboundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> {

    protected final O distribution;
    protected Consumer<T> setStateNodeFunc;
    protected boolean hasBeenConsumed = false;

    /**
     * Constructs an unbound distribution wrapping the given BEAST distribution, together with the
     * setter that writes a value into whichever input of that distribution the value belongs to.
     */
    public UnboundDistribution(O distribution, Consumer<T> setStateNodeFunc) {
        this.distribution = distribution;
        this.setStateNodeFunc = setStateNodeFunc;
    }

    /**
     * Wires the given observed value into the distribution and registers the distribution as a
     * likelihood under the given id in the given BEAST state — the observed-data counterpart of
     * {@link BoundDistribution#draw}.
     *
     * <p>No state node and no operators are registered: the observed value is data, not something
     * the chain samples.
     *
     * @throws RuntimeException if this distribution has already been resolved
     */
    public void observeAs(BEASTState beastState, Object observedValue, String likelihoodId) {
        this.markAsConsumed();
        this.setStateNodeFunc.accept((T) observedValue);
        beastState.addLikelihoodDistribution(this.distribution, likelihoodId);
    }

    /**
     * Marks this distribution as resolved, so that it is wired into the BEAST state exactly once.
     * Resolving it twice would register the same distribution as both a prior and a likelihood, or
     * register it under two ids, which always indicates a bug in the calling tile.
     *
     * @throws RuntimeException if this distribution has already been resolved
     */
    protected void markAsConsumed() {
        if (this.hasBeenConsumed) {
            throw new RuntimeException("Trying to consume a distribution twice. This should not happen.");
        }
        this.hasBeenConsumed = true;
    }

    /**
     * Returns the wrapped BEAST distribution.
     */
    public O getDistribution() {
        return distribution;
    }
}
