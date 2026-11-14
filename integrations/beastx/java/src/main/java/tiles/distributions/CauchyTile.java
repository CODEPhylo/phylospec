package tiles.distributions;

import dr.inference.distribution.CauchyDistribution;
import dr.inference.distribution.DistributionLikelihood;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.Set;

public class CauchyTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Cauchy";
    }

    // BEAST X's CauchyDistribution stores plain doubles, so sampled values would be frozen.
    GeneratorTileInput<RealScalar<Real>, BeastXState> locationInput =
            new GeneratorTileInput<>(
                    "location",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> scaleInput =
            new GeneratorTileInput<>(
                    "scale",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<Real> location =
                this.locationInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> scale =
                this.scaleInput.apply(beastState, indexVariables);

        CauchyDistribution distribution =
                new CauchyDistribution(location.get(), scale.get());

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distribution);

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(0.0, Real.INSTANCE);

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(state.getParameter())
        );
    }
}