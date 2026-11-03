package tiles.distributions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import operators.IntScalarUniformOperator;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BoundDistribution;

public class DiscreteUniformTile extends GeneratorTile<BoundDistribution<IntScalarParam<Int>, IntUniform>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteUniform";
    }

    GeneratorTileInput<IntScalar<? extends Int>, BEASTState> lowerInput = new GeneratorTileInput<>("lower");
    GeneratorTileInput<IntScalar<? extends Int>, BEASTState> upperInput = new GeneratorTileInput<>("upper");

    @Override
    public BoundDistribution<IntScalarParam<Int>, IntUniform> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        IntScalar<? extends Int> lower = this.lowerInput.apply(beastState, indexVariables);
        IntScalar<? extends Int> upper = this.upperInput.apply(beastState, indexVariables);

        IntUniform distribution = new IntUniform();
        // BEAST only reads these bounds. Keep their original objects, domains,
        // and live dependencies on the sampled states.
        @SuppressWarnings("unchecked")
        IntScalar<Int> nativeLower = (IntScalar<Int>) lower;
        @SuppressWarnings("unchecked")
        IntScalar<Int> nativeUpper = (IntScalar<Int>) upper;
        beastState.setInput(distribution, distribution.lowerInput, nativeLower);
        beastState.setInput(distribution, distribution.upperInput, nativeUpper);

        // Promote before adding so even a full-width integer interval has a legal midpoint.
        int initialValue = (int) (((long) lower.get() + upper.get()) / 2);
        IntScalarParam<Int> defaultState = new IntScalarParam<>(initialValue, Int.INSTANCE);

        return new BoundDistribution<>(
                distribution,
                defaultState,
                stateNode -> beastState.setInput(distribution, distribution.paramInput, stateNode),
                (stateNode, state) -> {
                    IntScalarUniformOperator operator = new IntScalarUniformOperator();
                    state.setInput(operator, operator.parameterInput, stateNode);
                    state.setInput(operator, operator.m_pWeight, 10.0);
                    return List.of(operator);
                });
    }
}
