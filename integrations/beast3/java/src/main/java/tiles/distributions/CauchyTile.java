package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

public class CauchyTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Cauchy>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Cauchy";
    }

    GeneratorTileInput<RealScalar<Real>> locationInput = new GeneratorTileInput<>("location");
    GeneratorTileInput<RealScalar<PositiveReal>> scaleInput = new GeneratorTileInput<>("scale");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Cauchy> applyTile(BEASTState beastState) {
        RealScalar<Real> location = this.locationInput.apply(beastState);
        RealScalar<PositiveReal> scale = this.scaleInput.apply(beastState);

        Cauchy distribution = new Cauchy();
        beastState.setInput(distribution, distribution.locationInput, location);
        beastState.setInput(distribution, distribution.scaleInput, scale);

        RealScalarParam<Real> defaultState = new RealScalarParam<>();

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
