package tiling.model;

import java.util.function.Consumer;

/**
 * Connects a BEAST X likelihood to the PhyloSpec state object it generates or
 * observes.
 *
 * <p>Generator tiles create the likelihood and a default state object
 * separately. This wrapper keeps those two pieces together and provides the
 * binding step that attaches either the default state object or an observed
 * state object to the underlying BEAST X likelihood.</p>
 */
public class BoundDistribution<T, O> {

    public final O distribution;
    public T stateNode;
    private final Consumer<T> setStateNodeFunc;

    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc) {
        this.distribution = distribution;
        this.stateNode = defaultState;
        this.setStateNodeFunc = setStateNodeFunc;
    }

    public void bind() {
        this.setStateNodeFunc.accept(this.stateNode);
    }

    public void bind(T observedStateNode) {
        this.setStateNodeFunc.accept(observedStateNode);
        this.stateNode = observedStateNode;
    }
}
