package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class RangeTile extends GeneratorTile<IntVectorParam<Int>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "range";
    }

    GeneratorTileInput<IntScalarParam<? extends Int>, BEASTState> startInput =
            new GeneratorTileInput<>("start", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));
    GeneratorTileInput<IntScalarParam<? extends Int>, BEASTState> endInput =
            new GeneratorTileInput<>("end", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public IntVectorParam<Int> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        int start = this.startInput.apply(beastState, indexVariables).get();
        int end = this.endInput.apply(beastState, indexVariables).get();

        long length = Math.abs((long) end - start) + 1L;
        if (length > Integer.MAX_VALUE) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Range too large.",
                    "This range contains more than Integer.MAX_VALUE elements.",
                    List.of("range(start=1, end=10)"));
        }

        int[] values = new int[(int) length];
        int step = start <= end ? 1 : -1;

        for (int i = 0; i < values.length; i++) {
            values[i] = start + i * step;
        }

        IntVectorParam<Int> result = new IntVectorParam<>();
        result.domainTypeInput.setValue(Int.INSTANCE, result);

        // Match the vector bounds to the declared integer domain before validation.
        result.setLower(Int.INSTANCE.getLower());
        result.setUpper(Int.INSTANCE.getUpper());

        result.valuesInput.setValue(IntStream.of(values).boxed().toList(), result);
        result.initAndValidate();
        return result;
    }
}
