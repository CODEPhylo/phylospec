package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Tensor;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class NumRowsTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numRows";
    }

    GeneratorTileInput<Tensor<?, ?>> matrixInput = new GeneratorTileInput<>("matrix");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Tensor<?, ?> matrix = this.matrixInput.apply(beastState, indexVariables);
        return new IntScalarParam<>(matrix.shape()[0], NonNegativeInt.INSTANCE);
    }

}
