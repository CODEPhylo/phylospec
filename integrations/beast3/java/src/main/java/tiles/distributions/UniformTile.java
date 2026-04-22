package tiles.distributions;

import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.Map;

public class UniformTile extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Uniform>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Uniform";
    }

    GeneratorTileInput<RealScalar<Real>> lowerInput = new GeneratorTileInput<>("lower");
    GeneratorTileInput<RealScalar<Real>> upperInput = new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<RealScalarParam<Real>, Uniform> applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
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
