package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiling.*;
import tiles.MultiAstNodeTile;

public class OffsetTile extends MultiAstNodeTile<RealScalarParam<Real>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               Real x ~ $distribution
               x + $offset
               """;
    }

    TileInput<EvaluatedDistribution.WithInitialState<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>>> distributionInput = new TileInput<>(
            "$distribution"
    );
    TileInput<Double> offsetInput = new TileInput<>("$offset");

    @Override
    public RealScalarParam<Real> applyTile(BEASTState beastState) {
        EvaluatedDistribution.WithInitialState<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>> distribution = this.distributionInput.apply(
                beastState
        );
        Double offset = this.offsetInput.apply(beastState);

        OffsetReal offsetDistribution = new OffsetReal(distribution.distribution, offset);
        beastState.replaceDistribution(distribution.initialStateNode, offsetDistribution);

        return distribution.initialStateNode;
    }

    @Override
    protected Tile<?> createInstance() {
        return new OffsetTile();
    }
}
