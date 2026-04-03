package tiles.misc;

import beast.base.inference.StateNode;
import org.phylospec.ast.Stmt;
import patternmatching.*;
import tiles.AstNodeTile;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

public class DrawTile extends AstNodeTile<StateNode, Stmt.Draw> {

    TileInput<Stmt.Draw, EvaluatedDistribution<?, ?>> expressionInput = new TileInput<>(expr -> expr.expression);

    @Override
    public Class<Stmt.Draw> getTargetNodeType() {
        return Stmt.Draw.class;
    }

    @Override
    public StateNode applyTile(BEASTState beastState) {
        EvaluatedDistribution<?, ?> evaluatedDistribution = this.expressionInput.apply(beastState);

        // add to state

        beastState.addStateNode(evaluatedDistribution.stateNode());
        beastState.addDistribution(evaluatedDistribution.stateNode(), evaluatedDistribution.distribution());
        beastState.addOperators(evaluatedDistribution.operatorSet());

        // return the unwrapped state node

        return evaluatedDistribution.stateNode();
    }

    @Override
    public TypeToken<?> getTypeToken() {
        // we first try to get the state node type from the EvaluatedDistribution input
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

    @Override
    protected Tile<?> createInstance() {
        return new DrawTile();
    }

}
