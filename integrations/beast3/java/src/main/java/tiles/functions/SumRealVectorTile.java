package tiles.functions;

import beast.base.spec.domain.Real;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.GeneratorTile;
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
        RealVectorSum sum = new RealVectorSum();
        state.setInput(sum, sum.vectorInput, vector);
        state.addCalculationNode(sum, new TypeToken<RealVectorSum>() {}, getId("sum", indexVariables, ""));
        state.initBEASTObject(sum);
        return sum;
    }
}
