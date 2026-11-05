package tiles.functions;

import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.RealVectorFromScalars;
import tiling.RealVectorSum;

/** Maps the real vector overload of sum without copying the input values. */
public class SumRealVectorTile extends GeneratorTile<RealVectorSum, BEASTState> {

    GeneratorTileInput<RealVector<? extends Real>, BEASTState> vectorInput = new GeneratorTileInput<>("vector");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sum";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.functions.math");
    }

    @Override
    public RealVectorSum applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealVector<? extends Real> vector = vectorInput.apply(state, indexVariables);
        return bind(state, vector, getId("sum", indexVariables, ""));
    }

    private static RealVectorSum bind(BEASTState state, RealVector<? extends Real> vector, String id) {
        RealVectorSum sum = new RealVectorSum();
        state.setInput(sum, sum.vectorInput, vector);
        state.addCalculationNode(sum, new TypeToken<RealVectorSum>() {}, id);
        state.initBEASTObject(sum);
        return sum;
    }

    /** Adapts lists of scalar objects without copying their values or creating sampling state. */
    public static class Scalars extends GeneratorTile<RealVectorSum, BEASTState> {

        GeneratorTileInput<List<? extends RealScalar<? extends Real>>, BEASTState> vectorInput =
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
        public RealVectorSum applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            RealVectorFromScalars vector = new RealVectorFromScalars();
            for (RealScalar<? extends Real> value : vectorInput.apply(state, indexVariables)) {
                vector.valuesInput.setValue(value, vector);
            }
            return bind(state, vector, getId("sum", indexVariables, ""));
        }
    }
}
