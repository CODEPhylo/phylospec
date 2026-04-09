package tiles.functions;

import beast.base.spec.type.Tensor;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class NumRowsTile extends GeneratorTile<Integer> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "numRows";
    }

    TileInput<Tensor<?, ?>> matrixInput = new TileInput<>("matrix");

    @Override
    public Integer applyTile(BEASTState beastState) {
        Tensor<?, ?> matrix = this.matrixInput.apply(beastState);
        return matrix.shape()[0];
    }

}
