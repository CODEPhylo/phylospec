package tiling.model;

import java.util.function.Consumer;
import java.util.function.Function;

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

    public O distribution;
    public T stateNode;
    public final StartingTreeSpec startingTreeSpec;
    private final Consumer<T> setStateNodeFunc;
    private final Function<T, O> distributionFactory;

    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc) {
        this(distribution, defaultState, StartingTreeSpec.fixedNewick(), setStateNodeFunc);
    }

    public BoundDistribution(
            O distribution,
            T defaultState,
            StartingTreeSpec startingTreeSpec,
            Consumer<T> setStateNodeFunc
    ) {
        this.distribution = distribution;
        this.stateNode = defaultState;
        this.startingTreeSpec = startingTreeSpec;
        this.setStateNodeFunc = setStateNodeFunc;
        this.distributionFactory = null;
    }

    /**
     * Creates a distribution whose BEAST X likelihood must be constructed only
     * after its final state object is known. This is required for APIs such as
     * {@code SpeciationLikelihood}, which receive their tree in the constructor
     * and cannot be rebound afterwards.
     */
    public BoundDistribution(
            T defaultState,
            StartingTreeSpec startingTreeSpec,
            Function<T, O> distributionFactory
    ) {
        this.distribution = null;
        this.stateNode = defaultState;
        this.startingTreeSpec = startingTreeSpec;
        this.setStateNodeFunc = null;
        this.distributionFactory = distributionFactory;
    }

    public BoundDistribution(
            T defaultState,
            Function<T, O> distributionFactory
    ) {
        this(defaultState, StartingTreeSpec.fixedNewick(), distributionFactory);
    }

    public void bind() {
        bind(this.stateNode);
    }

    public void bind(T observedStateNode) {
        this.stateNode = observedStateNode;

        if (this.distributionFactory != null) {
            this.distribution = this.distributionFactory.apply(observedStateNode);
            return;
        }

        this.setStateNodeFunc.accept(observedStateNode);
    }
}
