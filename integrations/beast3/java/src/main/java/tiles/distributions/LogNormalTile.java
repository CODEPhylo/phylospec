package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.LogNormal;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.BoundDistribution;
import tiling.Tile;

public class LogNormalTile extends GeneratorTile<BoundDistribution<RealScalarParam<PositiveReal>, LogNormal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "LogNormal";
    }

    TileInput<RealScalar<Real>> logMeanInput = new TileInput<>("logMean");
    TileInput<RealScalar<PositiveReal>> logSdInput = new TileInput<>("logSd");

    @Override
    public BoundDistribution<RealScalarParam<PositiveReal>, LogNormal> applyTile(BEASTState beastState) {
        RealScalar<Real> logMean = this.logMeanInput.apply(beastState);
        RealScalar<PositiveReal> logSd = this.logSdInput.apply(beastState);

        LogNormal distribution = new LogNormal();
        beastState.setInput(distribution, distribution.MParameterInput, logMean);
        beastState.setInput(distribution, distribution.SParameterInput, logSd);

        RealScalarParam<PositiveReal> defaultState = new RealScalarParam<>(0.5, PositiveReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
