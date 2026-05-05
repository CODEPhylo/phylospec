package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.IdentityHashMap;

public class UniformTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Uniform>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Uniform";
    }

    GeneratorTileInput<RealScalar<Real>, BEASTState> lowerInput = new GeneratorTileInput<>("lower");
    GeneratorTileInput<RealScalar<Real>, BEASTState> upperInput = new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Uniform> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<Real> lower = this.lowerInput.apply(beastState, indexVariables);
        RealScalar<Real> upper = this.upperInput.apply(beastState, indexVariables);

        Uniform distribution = new Uniform();
        beastState.setInput(distribution, distribution.lowerInput, lower);
        beastState.setInput(distribution, distribution.upperInput, upper);

        RealScalarParam<Real> defaultState = new RealScalarParam<>();

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
