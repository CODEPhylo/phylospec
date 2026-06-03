package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class IndexVariableTile extends AstNodeTile<IntScalar<Int>, Expr.Variable, BeastXState> {

    @Override
    public IntScalar<Int> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        for (Expr.Variable definition : this.getIndexVariables()) {
            if (definition.variableName.equals(this.getRootNode().variableName)) {
                return new BeastXIntScalarParam<>(
                        indexVariables.get(definition),
                        Int.INSTANCE
                );
            }
        }

        throw new RuntimeException(
                "Tile applied for a variable AST node which is not an index variable. This should not happen."
        );
    }
}