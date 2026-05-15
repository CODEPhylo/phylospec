package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.GammaDistributionModel;
import dr.inference.model.Parameter;
import dr.util.Attribute;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class GammaTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Gamma";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> shapeInput =
            new GeneratorTileInput<>("shape");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> rateInput =
            new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> shape =
                this.shapeInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> rate =
                this.rateInput.apply(beastState, indexVariables);

        Parameter shapeParameter = new Parameter.Default(shape.get());
        Parameter rateParameter = new Parameter.Default(rate.get());

        GammaDistributionModel distributionModel =
                new GammaDistributionModel(
                        GammaDistributionModel.GammaParameterizationType.ShapeRate,
                        shapeParameter,
                        rateParameter,
                        0.0
                );

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
