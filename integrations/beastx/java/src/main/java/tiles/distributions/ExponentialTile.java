package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.ExponentialDistributionModel;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.Set;

public class ExponentialTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Exponential";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateInput =
            new GeneratorTileInput<>(
                    "rate",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);

        Parameter mean = new Parameter.Default(1.0 / rate.get());

        ExponentialDistributionModel distributionModel =
                new ExponentialDistributionModel(mean);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        Parameter.Default defaultParameter = new Parameter.Default(1.0);
        defaultParameter.addBounds(0.0, Double.POSITIVE_INFINITY);

        BeastXRealScalarParam<PositiveReal> defaultState =
                new BeastXRealScalarParam<>(defaultParameter, PositiveReal.INSTANCE);

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
