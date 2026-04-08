package tiling;

import beast.base.inference.StateNode;

import java.util.function.Consumer;

public class UnboundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> {

    public final O distribution;
    protected Consumer<T> setStateNodeFunc;

    public UnboundDistribution(O distribution, Consumer<T> setStateNodeFunc) {
        this.distribution = distribution;
        this.setStateNodeFunc = setStateNodeFunc;
    }

    public void bind(Object observedStateNode) {
        this.setStateNodeFunc.accept((T) observedStateNode);
    }

}
