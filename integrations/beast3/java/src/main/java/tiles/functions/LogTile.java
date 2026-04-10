package tiles.functions;

import tiling.BEASTState;
import tiles.GeneratorTile;

public class LogTile extends GeneratorTile<Double> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "log";
    }

    GeneratorTileInput<Double> xInput = new GeneratorTileInput<>("x");
    GeneratorTileInput<Integer> basisInput = new GeneratorTileInput<>("base", false);

    @Override
    public Double applyTile(BEASTState beastState) {
        Double x = this.xInput.apply(beastState);
        Integer basis = this.basisInput.apply(beastState);

        if (basis == null) {
            // we use the natural logarithm
            return Math.log(x);
        } else {
            // we use the given basis
            return Math.log(x) / Math.log(basis);
        }
    }

}
