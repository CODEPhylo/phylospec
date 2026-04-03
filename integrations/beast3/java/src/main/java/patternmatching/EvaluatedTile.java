package patternmatching;

import java.lang.reflect.Type;

public record EvaluatedTile (
        Tile<?> tile,
        Object generatedObject,
        Type generatedType,
        int weight
) {
    public EvaluatedTile(
            Tile<?> tile,
            Object generatedObject,
            Type generatedType
    ) {
        this(tile, generatedObject, generatedType, 0);
    }
}
