package tiles.distributions;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.BoundDistribution;

public class ExponentialTile extends GeneratorTile<BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Exponential";
    }

    GeneratorTileInput<Double> rateInput = new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<RealScalarParam<NonNegativeReal>, Exponential> applyTile(BEASTState beastState) {
        Double rate = this.rateInput.apply(beastState);
        RealScalarParam<PositiveReal> mean = new RealScalarParam<>(1.0 / rate, PositiveReal.INSTANCE);

        Exponential distribution = new Exponential();
        beastState.setInput(distribution, distribution.meanInput, mean);

        RealScalarParam<NonNegativeReal> defaultState = new RealScalarParam<>(1.0, NonNegativeReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
