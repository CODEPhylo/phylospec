package tiles.functions;

import beast.base.spec.type.Tensor;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class NumColsTile extends GeneratorTile<Integer> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numCols";
    }

    GeneratorTileInput<Tensor<?, ?>> matrixInput = new GeneratorTileInput<>("matrix");

    @Override
    public Integer applyTile(BEASTState beastState) {
        Tensor<?, ?> matrix = this.matrixInput.apply(beastState);
        return matrix.shape()[1];
    }

}
