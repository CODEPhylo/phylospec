package tiling;

import dr.inference.distribution.DistributionLikelihood;

import java.util.function.Consumer;

public class BoundDistribution<T extends BeastXParam, O extends DistributionLikelihood> {

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
