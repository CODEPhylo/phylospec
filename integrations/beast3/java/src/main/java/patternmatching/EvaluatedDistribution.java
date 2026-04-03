package patternmatching;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.util.Set;

public record EvaluatedDistribution<T extends StateNode, O extends beast.base.inference.Distribution>(
        O distribution,
        T stateNode,
        Set<Operator> operatorSet,
        boolean hasBeenDrawnBefore) {
    public EvaluatedDistribution(
            O distribution,
            T stateNode,
            Set<Operator> operatorSet
    ) {
        this(distribution, stateNode, operatorSet, false);
    }
}
