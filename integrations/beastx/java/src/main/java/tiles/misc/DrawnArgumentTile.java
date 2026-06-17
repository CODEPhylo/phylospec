package tiles.misc;

import dr.inference.distribution.AbstractDistributionLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.AstNodeTile;
import tiling.params.BeastXParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.OptionalLong;
import java.util.IdentityHashMap;

public class DrawnArgumentTile extends AstNodeTile<BeastXParam, Expr.DrawnArgument, BeastXState> {

    AstNodeTileInput<
            BoundDistribution<? extends BeastXParam, ? extends AbstractDistributionLikelihood>,
            Expr.DrawnArgument,
            BeastXState
            > expressionInput =
            new AstNodeTileInput<>(
                    "expression",
                    expr -> expr.expression
            );

    @Override
    public BeastXParam applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<? extends BeastXParam, ? extends AbstractDistributionLikelihood> evaluatedDistribution =
                this.expressionInput.apply(beastState, indexVariables);

        String id =
                this.getId(this.getRootNode().name, indexVariables, "");

        evaluatedDistribution.bind();

        beastState.addStateNode(
                evaluatedDistribution.stateNode,
                this.getTypeToken(),
                id
        );

        beastState.addPriorDistribution(
                evaluatedDistribution.stateNode,
                evaluatedDistribution.distribution,
                id + "_prior"
        );

        return evaluatedDistribution.stateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        TypeToken<?> resolved =
                TypeToken.firstConcreteTypeArg(this.expressionInput.getTypeToken());

        if (resolved != null) {
            return resolved;
        }

        return new TypeToken<BeastXParam>() {};
    }

    @Override
    public OptionalLong getFixedOutputSize() {
        return this.expressionInput.getTile() == null
                ? OptionalLong.empty()
                : this.expressionInput.getTile().getFixedOutputSize();
    }

}
