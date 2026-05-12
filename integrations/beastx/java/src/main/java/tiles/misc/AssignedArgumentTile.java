package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.AstNodeTile;
import org.phylospec.tiling.tiles.TilePriority;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class AssignedArgumentTile extends AstNodeTile<Object, Expr.AssignedArgument, BeastXState> {

    AstNodeTileInput<Object, Expr.AssignedArgument, BeastXState> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Object applyTile(BeastXState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.expressionInput.getTypeToken();
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }
}