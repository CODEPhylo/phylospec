package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import tiling.*;
import tiles.TemplateTile;

public class OffsetTile extends TemplateTile<RealScalarParam<Real>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               Real x ~ $distribution
               x + $offset
               """;
    }

    TemplateTileInput<BoundDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>>> distributionInput = new TemplateTileInput<>(
            "$distribution"
    );
    TemplateTileInput<Double> offsetInput = new TemplateTileInput<>("$offset");

    @Override
    public RealScalarParam<Real> applyTile(BEASTState beastState) {
        BoundDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>> distribution = this.distributionInput.apply(
                beastState
        );
        Double offset = this.offsetInput.apply(beastState);

        OffsetReal offsetDistribution = new OffsetReal(distribution.distribution, offset);
        beastState.addStateNode(distribution.stateNode, this.getTypeToken(), "offset");
        beastState.addPriorDistribution(distribution.stateNode, offsetDistribution, "offset_prior");

        return distribution.stateNode;
    }

}
