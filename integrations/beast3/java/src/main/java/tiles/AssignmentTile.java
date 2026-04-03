package tiles;

import org.phylospec.ast.Stmt;
import patternmatching.*;

public class AssignmentTile extends AstNodeTile<Object, Stmt.Assignment> {

    TileInput<Stmt.Assignment, Object> expressionInput = new TileInput<>(expr -> expr.expression);

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

    @Override
    protected Tile<?> createInstance() {
        return new AssignmentTile();
    }

}
