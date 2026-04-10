package tiles.functions;

import tiles.GeneratorTile;
import tiling.BEASTState;

public class SqrtTile extends GeneratorTile<Double> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sqrt";
    }

    GeneratorTileInput<Double> xInput = new GeneratorTileInput<>("x");

    @Override
    public Double applyTile(BEASTState beastState) {
        Double x = this.xInput.apply(beastState);
        return Math.sqrt(x);
    }

}
