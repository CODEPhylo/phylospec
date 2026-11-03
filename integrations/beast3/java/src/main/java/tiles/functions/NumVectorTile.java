package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Vector;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;

public class NumVectorTile extends GeneratorTile<IntScalarParam<NonNegativeInt>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    // Element values may be sampled while the number of entries remains fixed.
    GeneratorTileInput<? extends Vector<?, ?>, BEASTState> vectorInput = new GeneratorTileInput<>("vector");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Vector<?, ?> vector = this.vectorInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(vector.shape()[0], NonNegativeInt.INSTANCE);
    }
}
