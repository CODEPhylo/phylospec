package tiles.functions;

import beast.base.spec.type.Vector;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class NumTile extends GeneratorTile<Integer> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "num";
    }

    TileInput<Vector<?, ?>> vectorInput = new TileInput<>("vector");

    @Override
    public Integer applyTile(BEASTState beastState) {
        Vector<?, ?> vector = this.vectorInput.apply(beastState);
        return vector.shape()[0];
    }

}
