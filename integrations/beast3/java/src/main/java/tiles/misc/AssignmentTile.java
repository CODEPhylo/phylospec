package tiles.misc;

import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import tiling.*;
import tiles.AstNodeTile;

import java.util.IdentityHashMap;

public class AssignmentTile extends AstNodeTile<Object, Stmt.Assignment> {

    AstNodeTileInput<Object, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Object applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.LOW;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }

}
