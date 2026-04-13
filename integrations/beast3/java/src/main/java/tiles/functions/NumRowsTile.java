package tiles.functions;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.type.Tensor;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class NumRowsTile extends GeneratorTile<IntScalarParam<NonNegativeInt>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numRows";
    }

    GeneratorTileInput<Tensor<?, ?>> matrixInput = new GeneratorTileInput<>("matrix");

    @Override
    public IntScalarParam<NonNegativeInt> applyTile(BEASTState beastState) {
        Tensor<?, ?> matrix = this.matrixInput.apply(beastState);
        return new IntScalarParam<>(matrix.shape()[0], NonNegativeInt.INSTANCE);
    }

}
