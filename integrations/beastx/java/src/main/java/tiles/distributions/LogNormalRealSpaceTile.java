package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.Parameter;
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

public class LogNormalRealSpaceTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "LogNormal";
    }

    GeneratorTileInput<RealScalar<Real>, BeastXState> meanInput =
            new GeneratorTileInput<>("mean");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> logSdInput =
            new GeneratorTileInput<>("logSd");

    @Override
    public BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<Real> mean =
                this.meanInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> logSd =
                this.logSdInput.apply(beastState, indexVariables);

        Parameter meanParameter = new Parameter.Default(mean.get());
        Parameter logSdParameter = new Parameter.Default(logSd.get());

        LogNormalDistributionModel distributionModel =
                new LogNormalDistributionModel(
                        meanParameter,
                        logSdParameter,
                        0.0,
                        true
                );

        DistributionLikelihood likelihood =
                new DistributionLikelihood(distributionModel);

        Parameter.Default defaultParameter = new Parameter.Default(0.5);
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
