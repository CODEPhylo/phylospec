package tiles.functions;

import beast.base.spec.domain.Int;
import beast.base.spec.type.IntVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
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
        IntVectorSum sum = new IntVectorSum();
        state.setInput(sum, sum.vectorInput, vector);
        state.addCalculationNode(sum, new TypeToken<IntVectorSum>() {}, getId("sum", indexVariables, ""));
        state.initBEASTObject(sum);
        return sum;
    }
}
