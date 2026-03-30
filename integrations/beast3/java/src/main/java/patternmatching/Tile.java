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
}
