package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Gamma;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class GammaTile extends GeneratorTile<BoundDistribution<RealScalarParam<PositiveReal>, Gamma>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Gamma";
    }

    GeneratorTileInput<RealScalar<PositiveReal>> shapeInput = new GeneratorTileInput<>("shape");
    GeneratorTileInput<RealScalar<PositiveReal>> rateInput = new GeneratorTileInput<>("rate");

    @Override
    public BoundDistribution<RealScalarParam<PositiveReal>, Gamma> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> shape = this.shapeInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);

        Gamma distribution = new Gamma();
        beastState.setInput(distribution, distribution.alphaInput, shape);
        // betaInput is the rate (lambda) parameter in BEAST3's Gamma
        beastState.setInput(distribution, distribution.betaInput, rate);

        RealScalarParam<PositiveReal> defaultState = new RealScalarParam<>(1.0, PositiveReal.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
