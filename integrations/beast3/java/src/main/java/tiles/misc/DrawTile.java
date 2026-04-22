package tiles.misc;

import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.ast.Stmt;
import tiling.*;
import tiles.AstNodeTile;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Map;

public class DrawTile extends AstNodeTile<StateNode, Stmt.Draw> {

    AstNodeTileInput<BoundDistribution<?, ?>, Stmt.Draw> expressionInput = new AstNodeTileInput<>(
            "expression", expr -> expr.expression
    );

    @Override
    public Class<Stmt.Draw> getTargetNodeType() {
        return Stmt.Draw.class;
    }

    @Override
    public StateNode applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        BoundDistribution<?, ?> evaluatedDistribution = this.expressionInput.apply(beastState, indexVariables);

        // we initialize the state node and add it to the BEAST state
        evaluatedDistribution.bind();
        beastState.addStateNode(evaluatedDistribution.stateNode, this.getTypeToken(), this.getRootNode().name);
        beastState.addPriorDistribution(evaluatedDistribution.stateNode, evaluatedDistribution.distribution, this.getRootNode().name + "_prior");

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
