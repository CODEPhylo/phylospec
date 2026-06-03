package tiles.observations;

import dr.inference.distribution.AbstractDistributionLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.domain.Int;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.types.IntScalar;
import tiling.params.BeastXIntScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;

public class ObservedAsIntTile extends TemplateTile<IntScalar<? extends Int>, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<BoundDistribution<BeastXIntScalarParam<Int>, ? extends AbstractDistributionLikelihood>, BeastXState> distributionInput =
            new TemplateTileInput<>("$distribution");

    TemplateTileInput<IntScalar<? extends Int>, BeastXState> observationInput =
            new TemplateTileInput<>("$observation");

    @Override
    public IntScalar<? extends Int> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<BeastXIntScalarParam<Int>, ? extends AbstractDistributionLikelihood> distribution =
                this.distributionInput.apply(beastState, indexVariables);

        IntScalar<? extends Int> observedValue =
                this.observationInput.apply(beastState, indexVariables);

        BeastXIntScalarParam<Int> observedStateNode =
                new BeastXIntScalarParam<>(
                        observedValue.get(),
                        Int.INSTANCE
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
