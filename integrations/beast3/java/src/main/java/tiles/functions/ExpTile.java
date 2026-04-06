package tiles.functions;

import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class ExpTile extends GeneratorTile<Double> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    Input<Double> xInput = new Input<>("x");

    @Override
    public Double applyTile(BEASTState beastState) {
        Double variable = this.xInput.apply(beastState);
        return Math.exp(variable);
    }

    @Override
    protected Tile<?> createInstance() {
        return new ExpTile();
    }

}
