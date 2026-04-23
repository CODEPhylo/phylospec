package tiles.misc;

import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import beastconfig.BEASTState;
import tiling.TypeToken;

import java.util.IdentityHashMap;

public class AssignedArgumentTile extends AstNodeTile<Object, Expr.AssignedArgument> {

    AstNodeTileInput<Object, Expr.AssignedArgument> expressionInput = new AstNodeTileInput<>("expression", expr -> expr.expression);

    @Override
    public Object applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.expressionInput.getTypeToken();
    }

}
