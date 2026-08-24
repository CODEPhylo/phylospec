package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Tensor;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;

public class NumColsTile extends GeneratorTile<IntScalarParam<NonNegativeInt>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numCols";
    }

    GeneratorTileInput<Tensor<?, ?>, BEASTState> matrixInput = new GeneratorTileInput<>("matrix");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tensor<?, ?> matrix = this.matrixInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(matrix.shape()[1], NonNegativeInt.INSTANCE);
    }
}
