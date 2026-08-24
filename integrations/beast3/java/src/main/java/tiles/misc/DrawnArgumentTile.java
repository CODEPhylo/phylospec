package tiles.misc;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.AstNodeTile;
import tiling.BoundDistribution;

public class DrawnArgumentTile extends AstNodeTile<StateNode, Expr.DrawnArgument, BEASTState> {

    AstNodeTileInput<BoundDistribution<?, ?>, Expr.DrawnArgument, BEASTState> expressionInput =
            new AstNodeTileInput<>("expression", expr -> expr.expression);

    @Override
    public StateNode applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        BoundDistribution<?, ?> evaluatedDistribution = this.expressionInput.apply(beastState, indexVariables);

        // construct ID

        String id = this.getId(this.getRootNode().name, indexVariables, "");

        // we delegate wiring the state node, registering it (and its prior and operators)
        // in the BEAST state, to the drawable distribution itself

        return evaluatedDistribution.draw(beastState, this.getTypeToken(), id);
    }

    @Override
    public TypeToken<?> getTypeToken() {
        // we first try to get the state node type from the DrawableDistribution input
        TypeToken<?> resolved = TypeToken.firstConcreteTypeArg(this.expressionInput.getTypeToken());
        if (resolved != null) return resolved;

        // we cannot obtain the type yet (e.g. before tiling)
        // we return a more general StateNode
        return new TypeToken<StateNode>() {};
    }
}
