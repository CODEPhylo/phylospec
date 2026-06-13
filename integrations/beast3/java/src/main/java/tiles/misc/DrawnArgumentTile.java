package tiles.misc;

import beast.base.inference.StateNode;
import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;
import tiling.TypeToken;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

public class DrawnArgumentTile extends AstNodeTile<StateNode, Expr.DrawnArgument> {

    AstNodeTileInput<BoundDistribution<?, ?>, Expr.DrawnArgument> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public StateNode applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        BoundDistribution<?, ?> evaluatedDistribution = this.expressionInput.apply(beastState, indexVariables);

        // construct ID

        String id = this.getId(this.getRootNode().name, indexVariables, "");

        // we initialize the state node and add it to the BEAST state

        evaluatedDistribution.bind();
        beastState.addStateNode(evaluatedDistribution.stateNode, this.getTypeToken(), id);
        beastState.addPriorDistribution(evaluatedDistribution.stateNode, evaluatedDistribution.distribution, id + "_prior");

        // we return the initialized state node
        return evaluatedDistribution.stateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        // we first try to get the state node type from the BoundDistribution input
        TypeToken<?> resolved = TypeToken.firstConcreteTypeArg(this.expressionInput.getTypeToken());
        if (resolved != null) return resolved;

        // we cannot obtain the type yet (e.g. before tiling)
        // we return a more general StateNode
        return new TypeToken<StateNode>() {};
    }

}
