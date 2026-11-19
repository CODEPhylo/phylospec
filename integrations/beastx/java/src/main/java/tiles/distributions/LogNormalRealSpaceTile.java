package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.LogNormalDistributionModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXLogNormalMu;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXParameters;

import java.util.IdentityHashMap;

public class LogNormalRealSpaceTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "LogNormal";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> meanInput =
            new GeneratorTileInput<>("mean");

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> logSdInput =
            new GeneratorTileInput<>("logSd");

    @Override
    public BoundDistribution<BeastXRealScalarParam<PositiveReal>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> mean =
                this.meanInput.apply(beastState, indexVariables);

        RealScalar<PositiveReal> logSd =
                this.logSdInput.apply(beastState, indexVariables);

        Parameter meanParameter =
                BeastXParameters.toParameter(mean);

        Parameter logSdParameter =
                BeastXParameters.toParameter(logSd);

        // PhyloSpec's mean is on the real scale and logSd on the log scale. BEAST X's real-space
        // parameterization would read logSd as a real-space standard deviation, so we use
        // (mu, sigma) on the log scale with mu = log(mean) - logSd^2 / 2 instead.
        LogNormalDistributionModel distributionModel =
                new LogNormalDistributionModel(
                        LogNormalDistributionModel.Parameterization.MU_SIGMA,
                        new BeastXLogNormalMu(meanParameter, logSdParameter),
                        logSdParameter,
                        0.0
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
                state -> likelihood.addData(state.getParameter())
        );
    }
}
