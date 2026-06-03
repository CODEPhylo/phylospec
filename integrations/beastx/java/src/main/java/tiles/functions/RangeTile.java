package tiles.functions;

import org.phylospec.ast.Expr;
import org.phylospec.domain.Int;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import tiling.params.BeastXIntVectorParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;
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

        int num = Math.abs(end - start);

        if (num == 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Empty range.",
                    "You use a range with a length of 0. Specify a different start and end.",
                    List.of("range(start=3, end=10)")
            );
        }

        int[] values = new int[num];
        int step = start < end ? 1 : -1;

        for (int i = 0; i < num; i++) {
            values[i] = start + i * step;
        }

        return new BeastXIntVectorParam<>(values, Int.INSTANCE);
    }
}
