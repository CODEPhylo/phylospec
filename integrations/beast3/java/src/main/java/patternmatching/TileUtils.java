package patternmatching;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

public class TileUtils {
    public static Type getParametricType(Object object, int index) {
        // parse the type parameter T
        Type superclass = object.getClass().getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[index];
        } else {
            return null;
        }
    }

    public static <T> EvaluatedTile getBestInput(Set<EvaluatedTile> possibleTiles, TypeToken<T> expectedType) {
        if (possibleTiles == null || possibleTiles.isEmpty()) {
            // no tiled input found. we cannot match this tile
            return null;
        }

        int lowestWeight = Integer.MAX_VALUE;
        EvaluatedTile bestTile = null;

        for (EvaluatedTile possibleTile : possibleTiles) {
            if (expectedType.isAssignableFrom(possibleTile.generatedType())) {
                // we can use the input tile to assign it to this argument
                // let's check if it is the tile with the lowest weight
                if (possibleTile.weight() < lowestWeight) {
                    lowestWeight = possibleTile.weight();
                    bestTile = possibleTile;
                }
            }
        }

        return bestTile;
    }
}
