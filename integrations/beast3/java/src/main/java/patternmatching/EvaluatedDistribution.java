package patternmatching;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.lang.reflect.Type;
import java.util.Set;

public record EvaluatedDistribution<T extends beast.base.inference.Distribution>(
        T distribution,
        StateNode stateNode,
        Type stateNodeType,
        Set<Operator> operatorSet) {
}
