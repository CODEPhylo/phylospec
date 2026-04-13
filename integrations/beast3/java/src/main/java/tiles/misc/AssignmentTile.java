package tiles.misc;

import org.phylospec.ast.Stmt;
import tiling.*;
import tiles.AstNodeTile;

public class AssignmentTile extends AstNodeTile<Object, Stmt.Assignment> {

    AstNodeTileInput<Object, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public Object applyTile(BEASTState beastState) {
        return this.expressionInput.apply(beastState);
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
