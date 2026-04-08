package tiles.functions;

import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class ExpTile extends GeneratorTile<Double> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    TileInput<Double> xInput = new TileInput<>("x");

    @Override
    public Double applyTile(BEASTState beastState) {
        Double variable = this.xInput.apply(beastState);
        return Math.exp(variable);
    }

}
