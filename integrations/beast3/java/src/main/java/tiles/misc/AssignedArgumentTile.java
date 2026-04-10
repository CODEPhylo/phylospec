package tiles.misc;

import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import tiling.BEASTState;
import tiling.TypeToken;

public class AssignedArgumentTile extends AstNodeTile<Object, Expr.AssignedArgument> {

    TileInput<Object, Expr.AssignedArgument> expressionInput = new TileInput<>("expression", expr -> expr.expression);

    @Override
    public Class<Expr.AssignedArgument> getTargetNodeType() {
        return Expr.AssignedArgument.class;
    }

    @Override
    public Object applyTile(BEASTState beastState, Expr.AssignedArgument node) {
        return this.expressionInput.apply(beastState);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.expressionInput.getTypeToken();
    }

}
