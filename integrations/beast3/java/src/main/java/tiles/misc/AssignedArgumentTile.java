package tiles.misc;

import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import beastconfig.BEASTState;
import tiling.TypeToken;

import java.util.Map;

public class AssignedArgumentTile extends AstNodeTile<Object, Expr.AssignedArgument> {

    AstNodeTileInput<Object, Expr.AssignedArgument> expressionInput = new AstNodeTileInput<>("expression", expr -> expr.expression);

    @Override
    public Class<Expr.AssignedArgument> getTargetNodeType() {
        return Expr.AssignedArgument.class;
    }

    @Override
    public Object applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        return this.expressionInput.apply(beastState, indexVariables);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.expressionInput.getTypeToken();
    }

}
