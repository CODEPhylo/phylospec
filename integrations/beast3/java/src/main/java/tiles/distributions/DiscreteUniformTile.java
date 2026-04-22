package tiles.distributions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.BoundDistribution;

import java.util.Map;

public class DiscreteUniformTile extends GeneratorTile<BoundDistribution<IntScalarParam<Int>, IntUniform>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteUniform";
    }

    GeneratorTileInput<IntScalar<Int>> lowerInput = new GeneratorTileInput<>("lower");
    GeneratorTileInput<IntScalar<Int>> upperInput = new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<IntScalarParam<Int>, IntUniform> applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        IntScalar<Int> lower = this.lowerInput.apply(beastState, indexVariables);
        IntScalar<Int> upper = this.upperInput.apply(beastState, indexVariables);

        IntUniform distribution = new IntUniform();
        beastState.setInput(distribution, distribution.lowerInput, lower);
        beastState.setInput(distribution, distribution.upperInput, upper);

        IntScalarParam<Int> defaultState = new IntScalarParam<>(0, Int.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                param -> beastState.setInput(distribution, distribution.paramInput, param)
        );
    }

}
