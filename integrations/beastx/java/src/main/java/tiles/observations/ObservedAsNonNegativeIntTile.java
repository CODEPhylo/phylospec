package tiles.observations;

import dr.inference.distribution.AbstractDistributionLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;

public class ObservedAsNonNegativeIntTile extends TemplateTile<IntScalar<? extends NonNegativeInt>, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, ? extends AbstractDistributionLikelihood>, BeastXState> distributionInput =
            new TemplateTileInput<>("$distribution");

    TemplateTileInput<IntScalar<? extends NonNegativeInt>, BeastXState> observationInput =
            new TemplateTileInput<>("$observation");

    @Override
    public IntScalar<? extends NonNegativeInt> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, ? extends AbstractDistributionLikelihood> distribution =
                this.distributionInput.apply(beastState, indexVariables);

        IntScalar<? extends NonNegativeInt> observedValue =
                this.observationInput.apply(beastState, indexVariables);

        BeastXIntScalarParam<NonNegativeInt> observedStateNode =
                new BeastXIntScalarParam<>(
                        observedValue.get(),
                        NonNegativeInt.INSTANCE
                );

        String prefix = "";

        if (this.getRootNode() instanceof Stmt stmt) {
            prefix = stmt.getName();
        }

        String id =
                this.getId(prefix, indexVariables, "likelihood");

        distribution.bind(observedStateNode);

        beastState.addLikelihoodDistribution(
                distribution.distribution,
                id
        );

        return observedStateNode;
    }

    @Override
    public TypeToken<?> getTypeToken() {
        return this.observationInput.getTypeToken();
    }
}