package tiles.distributions;

import dr.inference.distribution.CauchyDistribution;
import dr.inference.distribution.DistributionLikelihood;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class CauchyTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Cauchy";
    }

    GeneratorTileInput<RealScalar<Real>, BeastXState> locationInput =
            new GeneratorTileInput<>("location");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> scaleInput =
            new GeneratorTileInput<>("scale");

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
                state -> likelihood.addData(new Attribute.Default<>(
                        state.getParameter().getParameterName(),
                        new double[] {state.getParameter().getParameterValue(0)}
                ))
        );
    }
}

