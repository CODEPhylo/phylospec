package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;

public class NumListTile extends GeneratorTile<IntScalarParam<NonNegativeInt>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    // Element values may be sampled while the number of entries remains fixed.
    GeneratorTileInput<? extends List<?>, BEASTState> vectorInput = new GeneratorTileInput<>("vector");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        List<?> vector = this.vectorInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(vector.size(), NonNegativeInt.INSTANCE);
    }
}
