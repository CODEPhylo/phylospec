package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.BoundDistribution;

public class BetaTile extends GeneratorTile<BoundDistribution<RealScalarParam<UnitInterval>, Beta>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Beta";
    }

    TileInput<RealScalar<PositiveReal>> alphaInput = new TileInput<>("alpha");
    TileInput<RealScalar<PositiveReal>> betaInput = new TileInput<>("beta");

    @Override
    public BoundDistribution<RealScalarParam<UnitInterval>, Beta> applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> alpha = this.alphaInput.apply(beastState);
        RealScalar<PositiveReal> beta = this.betaInput.apply(beastState);

        Beta distribution = new Beta();
        beastState.setInput(distribution, distribution.alphaInput, alpha);
        beastState.setInput(distribution, distribution.betaInput, beta);

        RealScalarParam<UnitInterval> defaultState = new RealScalarParam<>(0.5, UnitInterval.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
