package tiles.misc;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.ast.Stmt;
import org.phylospec.typeresolver.Stochasticity;
import tiling.*;
import tiles.AstNodeTile;

import java.util.Set;

public class StateNodeAssignmentTile extends AstNodeTile<StateNode, Stmt.Assignment> {

    AstNodeTileInput<StateNode, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
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
    public StateNode applyTile(BEASTState beastState) {
        StateNode stateNode = this.expressionInput.apply(beastState);
        beastState.addStateNode(stateNode, this.getTypeToken(), this.getRootNode().name);
        return stateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }

}
