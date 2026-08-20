package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.OffsetReal;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.BoundDistribution;

public class OffsetTile extends TemplateTile<RealScalarParam<Real>, BEASTState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               Real x ~ $distribution
               x + $offset
               """;
    }

    TemplateTileInput<
                    BoundDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>>,
                    BEASTState>
            distributionInput = new TemplateTileInput<>("$distribution");
    TemplateTileInput<Double, BEASTState> offsetInput = new TemplateTileInput<>("$offset");

    @Override
    public RealScalarParam<Real> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        BoundDistribution<RealScalarParam<Real>, ? extends ScalarDistribution<RealScalar<Real>, Double>> distribution =
                this.distributionInput.apply(beastState, indexVariables);
        Double offset = this.offsetInput.apply(beastState, indexVariables);

        beastState.addStateNodeWithoutOperators(distribution.getStateNode(), this.getTypeToken(), "offset");
        beastState.addOperators(
                distribution.getStateNode(),
                OperatorSelector.getDefaultOperators(distribution.getStateNode(), beastState));

        OffsetReal offsetDistribution = new OffsetReal(distribution.getDistribution(), offset);
        beastState.addPriorDistribution(distribution.getStateNode(), offsetDistribution, "offset_prior");

        return distribution.getStateNode();
    }
}
