package tiles.distributions;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiling.*;
import tiles.GeneratorTile;

import java.util.IdentityHashMap;

public class NormalTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Normal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Normal";
    }

    GeneratorTileInput<RealScalar<Real>> meanInput = new GeneratorTileInput<>("mean");
    GeneratorTileInput<RealScalar<PositiveReal>> sdInput = new GeneratorTileInput<>("sd");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Normal> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<Real> mean = this.meanInput.apply(beastState, indexVariables);
        RealScalar<PositiveReal> sd = this.sdInput.apply(beastState, indexVariables);

        Normal distribution = new Normal();
        beastState.setInput(distribution, distribution.meanInput, mean);
        beastState.setInput(distribution, distribution.sdInput, sd);

        RealScalarParam<Real> defaultState = new RealScalarParam<>(0.1, Real.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
