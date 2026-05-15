package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.PoissonDistributionModel;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXIntScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class PoissonTile extends GeneratorTile<
        BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Poisson";
    }

    GeneratorTileInput<RealScalar<NonNegativeReal>, BeastXState> rateInput =
            new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<BeastXIntScalarParam<NonNegativeInt>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<NonNegativeReal> rate =
                this.rateInput.apply(beastState, indexVariables);

        Parameter meanParameter = new Parameter.Default(rate.get());

        PoissonDistributionModel distributionModel =
                new PoissonDistributionModel(meanParameter);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        Parameter.Default defaultParameter = new Parameter.Default(0.0);
        defaultParameter.addBounds(0.0, Double.POSITIVE_INFINITY);

        BeastXIntScalarParam<NonNegativeInt> defaultState =
                new BeastXIntScalarParam<>(defaultParameter, NonNegativeInt.INSTANCE);

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
