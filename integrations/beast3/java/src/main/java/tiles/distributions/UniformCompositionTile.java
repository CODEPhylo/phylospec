package tiles.distributions;

import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.operator.DeltaExchangeOperator;
import beast.base.spec.inference.parameter.IntSimplexParam;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BoundDistribution;

public final class UniformCompositionTile
        extends GeneratorTile<BoundDistribution<IntSimplexParam<PositiveInt>, UniformComposition>, BEASTState> {
    GeneratorTileInput<IntScalar<? extends PositiveInt>, BEASTState> totalInput =
            new GeneratorTileInput<>("total", Set.of(Stochasticity.CONSTANT));
    GeneratorTileInput<IntScalar<? extends PositiveInt>, BEASTState> numInput =
            new GeneratorTileInput<>("num", Set.of(Stochasticity.CONSTANT));

    @Override
    public String getPhyloSpecGeneratorName() {
        return "UniformComposition";
    }

    @Override
    public BoundDistribution<IntSimplexParam<PositiveInt>, UniformComposition> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        int total = totalInput.apply(state, indices).get();
        int num = numInput.apply(state, indices).get();
        if (num < 1 || total < num) {
            throw new TileApplicationError(
                    "UniformComposition requires 1 <= num <= total.",
                    "Use a fixed positive number of groups no greater than the total.");
        }
        int[] values = new int[num];
        for (int i = 0; i < num; i++) values[i] = total / num + (i < total % num ? 1 : 0);
        var parameter = new IntSimplexParam<>(values, PositiveInt.INSTANCE, total);
        var distribution = new UniformComposition();
        return new BoundDistribution<>(
                distribution,
                parameter,
                value -> state.setInput(distribution, distribution.parameterInput, value),
                (value, graph) -> {
                    if (num == 1 || total == num) return List.of();
                    DeltaExchangeOperator operator = new DeltaExchangeOperator();
                    graph.setInput(operator, operator.ivparameterInput, value);
                    graph.setInput(operator, operator.integerOperatorInput, true);
                    graph.setInput(operator, operator.deltaInput, 2.0);
                    graph.setInput(operator, operator.m_pWeight, 5.0);
                    return List.of(operator);
                });
    }
}
