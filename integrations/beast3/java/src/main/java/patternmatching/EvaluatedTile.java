package patternmatching;

import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

public record EvaluatedTile (
        Tile tile,
        Object generatedObject,
        Type generatedType,
        Set<StateNode> newStateNodes,
        Map<StateNode, Distribution> newDistributions,
        Set<Operator> newOperators,
        int weight
) {
    public EvaluatedTile(
            Tile tile,
            Object generatedObject,
            Type generatedType,
            Set<StateNode> newStateNodes,
            Map<StateNode, Distribution> newDistributions,
            Set<Operator> newOperators
    ) {
        this(tile, generatedObject, generatedType, newStateNodes, newDistributions, newOperators, 0);
    }

    public EvaluatedTile(
            Tile tile,
            Object generatedObject,
            Type generatedType
    ) {
        this(tile, generatedObject, generatedType, Set.of(), Map.of(), Set.of(), 0);
    }

    public EvaluatedTile withWeight(int weight) {
        return new EvaluatedTile(
                tile, generatedObject, generatedType, newStateNodes, newDistributions, newOperators, weight
        );
    }
}
