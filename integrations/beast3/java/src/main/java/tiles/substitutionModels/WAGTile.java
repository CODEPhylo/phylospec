package tiles.substitutionModels;

import beast.base.spec.evolution.substitutionmodel.WAG;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class WAGTile extends GeneratorTile<WAG> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "wag";
    }

    Input<Simplex> baseFrequenciesInput = new Input<>("baseFrequencies", false);

    @Override
    public WAG applyTile(BEASTState beastState) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        WAG wag = new WAG();
        if (baseFrequencies != null) {
            wag.initByName("frequencies", baseFrequencies);
        } else {
            wag.initAndValidate();
        }

        return wag;
    }

    @Override
    protected Tile<?> createInstance() {
        return new WAGTile();
    }

}
