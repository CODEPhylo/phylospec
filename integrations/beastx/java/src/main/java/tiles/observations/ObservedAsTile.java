package tiles.observations;

import dr.inference.distribution.AbstractDistributionLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.domain.Real;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class ObservedAsTile extends TemplateTile<RealScalar<? extends Real>, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x ~ $distribution observed as $observation";
    }

    TemplateTileInput<BoundDistribution<BeastXRealScalarParam<Real>, ? extends AbstractDistributionLikelihood>, BeastXState> distributionInput =
            new TemplateTileInput<>("$distribution");

    TemplateTileInput<RealScalar<? extends Real>, BeastXState> observationInput =
            new TemplateTileInput<>("$observation");

    @Override
    public RealScalar<? extends Real> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<BeastXRealScalarParam<Real>, ? extends AbstractDistributionLikelihood> distribution =
                this.distributionInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> observedValue =
                this.observationInput.apply(beastState, indexVariables);

        BeastXRealScalarParam<Real> observedStateNode =
                new BeastXRealScalarParam<>(
                        observedValue.get(),
                        Real.INSTANCE
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