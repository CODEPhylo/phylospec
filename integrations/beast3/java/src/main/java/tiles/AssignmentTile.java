package tiles;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import patternmatching.*;

public class AssignmentTile extends AstNodeTile<StateNode, Stmt.Assignment> {

    TileInput<Stmt.Assignment, StateNode> expressionInput = new TileInput<>(
            expr -> expr.expression, new TypeToken<>() {
    }
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public StateNode applyTile(BEASTState beastState) {
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
        return new StateNodeAssignmentTile();
    }

}
