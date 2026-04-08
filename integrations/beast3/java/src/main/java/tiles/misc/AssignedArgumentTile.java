package tiles.misc;

import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import tiling.BEASTState;
import tiling.Tile;
import tiling.TypeToken;

public class AssignedArgumentTile extends AstNodeTile<Object, Expr.AssignedArgument> {

    TileInput<Expr.AssignedArgument, Object> expressionInput = new TileInput<>(expr -> expr.expression);

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

    @Override
    protected Tile<?> createInstance() {
        return new AssignedArgumentTile();
    }

}
