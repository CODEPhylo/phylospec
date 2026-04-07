package tiling;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.util.Set;
import java.util.function.Consumer;

public class EvaluatedDistribution<T extends StateNode, O extends beast.base.inference.Distribution> {

    public final O distribution;
    protected Consumer<T> setStateNodeFunc;

    public static class WithInitialState<T extends StateNode, O extends beast.base.inference.Distribution> extends EvaluatedDistribution<T, O> {

        public T initialStateNode;
        private final Set<Operator> operatorSet;

        public WithInitialState(O distribution, T initialStateNode, Set<Operator> operatorSet, Consumer<T> setStateNodeFunc) {
            super(distribution, setStateNodeFunc);
            this.initialStateNode = initialStateNode;
            this.operatorSet = operatorSet;

            this.setStateNodeFunc = x -> {
                this.initialStateNode = x;
                setStateNodeFunc.accept(x);
            };
        }

        public void initializeAsPriorOnState(BEASTState beastState) {
            beastState.addStateNode(this.initialStateNode);
            beastState.addDistribution(this.initialStateNode, this.distribution);
            beastState.addOperators(this.operatorSet);
        }

    }

    public EvaluatedDistribution(O distribution, Consumer<T> setStateNodeFunc) {
        this.distribution = distribution;
        this.setStateNodeFunc = setStateNodeFunc;
    }

    public void initializeAsLikelihoodOfState(StateNode observedStateNode, BEASTState beastState) {
        this.setStateNodeFunc.accept((T) observedStateNode);
        beastState.addDistribution(observedStateNode, this.distribution);
    }

}
