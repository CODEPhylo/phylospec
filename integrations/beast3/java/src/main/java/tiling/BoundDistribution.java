package tiling;

import beast.base.inference.StateNode;

import java.util.function.Consumer;

public class BoundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> extends UnboundDistribution<T, O> {

    public T stateNode;

    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc) {
        super(distribution, setStateNodeFunc);
        this.stateNode = defaultState;
    }

    public void bind() {
        this.setStateNodeFunc.accept(this.stateNode);
    }

    @Override
    public void bind(Object observedStateNode) {
        this.setStateNodeFunc.accept((T) observedStateNode);
        this.stateNode = (T) observedStateNode;
    }

}
