package tiles.misc;

import beast.base.inference.StateNode;
import org.phylospec.ast.Expr;
import tiles.AstNodeTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;
import tiling.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
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
        Type expressionType = this.expressionInput.getTypeToken().getType();
        if (expressionType instanceof ParameterizedType pt) {
            Type typeArg = pt.getActualTypeArguments()[0];
            if (!(typeArg instanceof TypeVariable) && !(typeArg instanceof WildcardType)) {
                return TypeToken.of(typeArg);
            }
        }

        // we cannot obtain the type yet (e.g. before tiling)
        // we return a more general StateNode
        return new TypeToken<StateNode>() {};
    }

}
