package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;

import java.util.Map;
import java.util.Set;

public abstract class AstNodeTile<T extends AstNode> extends Tile {
    public abstract Class<T> getTargetNodeType();

    @Override
    public Set<EvaluatedTile> tryToTile(AstNode node, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver) {
        if (this.getTargetNodeType().isAssignableFrom(node.getClass())) {
            return tryToTileExpr((T) node, inputTiles, typeResolver);
        } else {
            return Set.of();
        }
    }

    abstract public Set<EvaluatedTile> tryToTileExpr(T expr, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver);
}
