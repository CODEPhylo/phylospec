package tiles.functions;

import tiles.GeneratorTile;
import tiling.BEASTState;

public class ExpTile extends GeneratorTile<Double> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "exp";
    }

    GeneratorTileInput<Double> xInput = new GeneratorTileInput<>("x");

    @Override
    public Double applyTile(BEASTState beastState) {
        Double variable = this.xInput.apply(beastState);
        return Math.exp(variable);
    }

}
