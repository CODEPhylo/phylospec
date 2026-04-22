package tiles.misc;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.ast.Stmt;
import org.phylospec.typeresolver.Stochasticity;
import tiling.*;
import tiles.AstNodeTile;

import java.util.Map;
import java.util.Set;

public class StateNodeAssignmentTile extends AstNodeTile<StateNode, Stmt.Assignment> {

    AstNodeTileInput<StateNode, Stmt.Assignment> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    protected Set<Stochasticity> getCompatibleStochasticities() {
        return Set.of(Stochasticity.STOCHASTIC);
    }

    @Override
    public StateNode applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        StateNode stateNode = this.expressionInput.apply(beastState, indexVariables);
        beastState.addStateNode(stateNode, this.getTypeToken(), this.getRootNode().name);
        return stateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return expressionInput.getTypeToken();
    }

}
