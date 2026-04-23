package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import tiles.GeneratorTile;
import beastconfig.BEASTState;
import tiling.TileApplicationError;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class RangeTile extends GeneratorTile<IntVectorParam<Int>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "range";
    }

    GeneratorTileInput<IntScalarParam<? extends Int>> startInput = new GeneratorTileInput<>(
            "start", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );
    GeneratorTileInput<IntScalarParam<? extends Int>> endInput = new GeneratorTileInput<>(
            "end", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
    );

    @Override
    public IntVectorParam<Int> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
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
        int gap = start < end ? 1 : -1;

        for (int i = 0; i < num; i++) {
            values[i] = start + i*gap;
        }

        return new IntVectorParam<>(values, Int.INSTANCE);
    }

}
