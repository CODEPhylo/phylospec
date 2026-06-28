package tiles.misc;

import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.AstNodeTile;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeToken;

import java.util.IdentityHashMap;

public class AssignedArgumentTile extends AstNodeTile<Object, Expr.AssignedArgument, BEASTState> {

    AstNodeTileInput<Object, Expr.AssignedArgument, BEASTState> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Object applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.expressionInput.getTypeToken();
    }

}
