package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiling.*;
import tiles.TemplateTile;

import java.util.IdentityHashMap;

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
    public RealScalarParam<Real> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        BoundDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>> distribution = this.distributionInput.apply(
                beastState, indexVariables
        );
        Double offset = this.offsetInput.apply(beastState, indexVariables);

        OffsetReal offsetDistribution = new OffsetReal(distribution.distribution, offset);
        beastState.addStateNode(distribution.stateNode, this.getTypeToken(), "offset");
        beastState.addPriorDistribution(distribution.stateNode, offsetDistribution, "offset_prior");

        return distribution.stateNode;
    }

}
