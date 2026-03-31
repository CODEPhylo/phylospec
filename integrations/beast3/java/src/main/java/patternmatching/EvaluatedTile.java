package patternmatching;

import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.lang.reflect.Type;
import java.util.Set;

public record EvaluatedTile (
        Tile tile,
        Object generatedObject,
        Type generatedType,
        int score,
        Set<StateNode> newStateNodes,
        Set<Distribution> newDistributions,
        Set<Operator> newOperators
) {
    public EvaluatedTile(
            Tile tile,
            Object generatedObject,
            Type generatedType,
            int score
    ) {
        this(tile, generatedObject, generatedType, score, Set.of(), Set.of(), Set.of());
    }
}
