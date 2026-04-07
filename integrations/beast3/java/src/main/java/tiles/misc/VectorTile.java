package tiles.misc;

import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.VariableResolver;
import tiles.AstNodeTile;
import tiling.BEASTState;
import tiling.Tile;

import java.util.Map;
import java.util.Set;
import java.util.List;

public class VectorTile<T> extends AstNodeTile<T, Expr.Array> {

    T value;

    @Override
    public Class<Expr.Array> getTargetNodeType() {
        return Expr.Array.class;
    }

    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver) {
        if (!(node instanceof Expr.Array array)) return Set.of();

        // depending on the actual elements, we return different tiles

        List<Set<Tile<?>>> elementTiles = array.elements.stream().map(allInputTiles::get).toList();

        return Set.of();

    }

    @Override
    public T applyTile(BEASTState beastState) {
        return this.value;
    }

    @Override
    protected Tile<?> createInstance() {
        return new VectorTile<>();
    }
}
