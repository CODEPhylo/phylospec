package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;

import java.util.Map;
import java.util.Set;

public abstract class Tile {
    public abstract Set<EvaluatedTile> tryToTile(AstNode node, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver);
    public TilePriority getPriority() {
        return TilePriority.DEFAULT;
    }

    protected static <T> EvaluatedTile getBestInput(Set<EvaluatedTile> possibleTiles, TypeToken<T> expectedType) {
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
                if (possibleTile.score() < lowestWeight) {
                    lowestWeight = possibleTile.score();
                    bestTile = possibleTile;
                }
            }
        }

        return bestTile;
    }
}
