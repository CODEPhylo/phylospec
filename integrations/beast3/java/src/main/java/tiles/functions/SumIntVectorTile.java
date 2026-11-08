package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.IntVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.IntVectorFromScalars;
import tiling.IntVectorSum;

/** Maps the int vector overload of sum without copying the input values. */
public class SumIntVectorTile extends GeneratorTile<IntVectorSum, BEASTState> {

    GeneratorTileInput<IntVector<? extends Int>, BEASTState> vectorInput = new GeneratorTileInput<>("vector");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sum";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.math");
    }

    @Override
    public IntVectorSum applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        IntVector<? extends Int> vector = vectorInput.apply(state, indexVariables);
        return bind(state, vector, getId("sum", indexVariables, ""));
    }

    private static IntVectorSum bind(BEASTState state, IntVector<? extends Int> vector, String id) {
        IntVectorSum sum = new IntVectorSum();
        state.setInput(sum, sum.vectorInput, vector);
        state.addCalculationNode(sum, new TypeToken<IntVectorSum>() {}, id);
        state.initBEASTObject(sum);
        return sum;
    }

    /** Adapts lists of scalar objects without copying their values or creating sampling state. */
    public static class Scalars extends GeneratorTile<IntVectorSum, BEASTState> {

        GeneratorTileInput<List<? extends IntScalar<? extends Int>>, BEASTState> vectorInput =
                new GeneratorTileInput<>("vector");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "sum";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of("phylospec.functions.math");
        }

        @Override
        public IntVectorSum applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            IntVectorFromScalars vector = new IntVectorFromScalars();
            for (IntScalar<? extends Int> value : vectorInput.apply(state, indexVariables)) {
                vector.valuesInput.setValue(value, vector);
            }
            return bind(state, vector, getId("sum", indexVariables, ""));
        }
    }
}
