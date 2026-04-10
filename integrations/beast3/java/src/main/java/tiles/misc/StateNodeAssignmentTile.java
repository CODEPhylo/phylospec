package tiles.misc;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import org.phylospec.typeresolver.Stochasticity;
import tiling.*;
import tiles.AstNodeTile;

import java.util.Set;

public class StateNodeAssignmentTile extends AstNodeTile<StateNode, Stmt.Assignment> {

    TileInput<StateNode, Stmt.Assignment> expressionInput = new TileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Class<Stmt.Assignment> getTargetNodeType() {
        return Stmt.Assignment.class;
    }

    @Override
    protected Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.STOCHASTIC);
    }

    @Override
    public StateNode applyTile(BEASTState beastState, Stmt.Assignment node) {
        StateNode stateNode = this.expressionInput.apply(beastState);
        beastState.addStateNode(stateNode, this.getTypeToken(), node.name);
        return stateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }

}
