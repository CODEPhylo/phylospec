package tiles;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import patternmatching.*;

public class StateNodeAssignmentTile extends AstNodeTile<StateNode, Stmt.Assignment> {

    TileInput<Stmt.Assignment, StateNode> expressionInput = new TileInput<>(
            expr -> expr.expression
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    public StateNode applyTile(BEASTState beastState) {
        StateNode stateNode = this.expressionInput.apply(beastState);
        beastState.addStateNode(stateNode);
        return stateNode;
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
