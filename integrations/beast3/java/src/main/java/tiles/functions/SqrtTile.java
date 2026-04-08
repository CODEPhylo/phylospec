package tiles.functions;

import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class SqrtTile extends GeneratorTile<Double> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "sqrt";
    }

    TileInput<Double> xInput = new TileInput<>("x");

    @Override
    public Double applyTile(BEASTState beastState) {
        Double x = this.xInput.apply(beastState);
        return Math.sqrt(x);
    }

    @Override
    protected Tile<?> createInstance() {
        return new SqrtTile();
    }

}
