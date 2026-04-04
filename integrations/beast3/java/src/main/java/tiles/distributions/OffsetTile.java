package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import patternmatching.*;
import tiles.MultiAstNodeTile;

public class OffsetTile extends MultiAstNodeTile<RealScalarParam<Real>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               Real x ~ $distribution
               x + $offset
               """;
    }

    TileInput<EvaluatedDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>>> distributionInput = new TileInput<>(
            "$distribution"
    );
    TileInput<Double> offsetInput = new TileInput<>("$offset");

    @Override
    public RealScalarParam<Real> applyTile(BEASTState beastState) {
        EvaluatedDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>> distribution = this.distributionInput.apply(
                beastState
        );
        Double offset = this.offsetInput.apply(beastState);

        OffsetReal offsetDistribution = new OffsetReal(distribution.distribution(), offset);
        beastState.replaceDistribution(distribution.stateNode(), offsetDistribution);

        return distribution.stateNode();
    }

    @Override
    protected Tile<?> createInstance() {
        return new OffsetTile();
    }
}
