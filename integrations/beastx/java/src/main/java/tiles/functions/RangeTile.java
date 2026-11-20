package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import tiling.params.BeastXIntVectorParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.Set;

public class RangeTile extends GeneratorTile<IntVector<Int>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "range";
    }

    GeneratorTileInput<IntScalar<? extends Int>, BeastXState> startInput =
            new GeneratorTileInput<>(
                    "start",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<IntScalar<? extends Int>, BeastXState> endInput =
            new GeneratorTileInput<>(
                    "end",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    @Override
    public IntVector<Int> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        int start = this.startInput.apply(beastState, indexVariables).get();
        int end = this.endInput.apply(beastState, indexVariables).get();

        // Both start and end are included, so range(5, 5) is [5] and range(3, 1) is [3, 2, 1].
        int num = Math.abs(end - start) + 1;

        int[] values = new int[num];
        int step = start < end ? 1 : -1;

        for (int i = 0; i < num; i++) {
            values[i] = start + i * step;
        }

        return new BeastXIntVectorParam<>(values, Int.INSTANCE);
    }
}
