package tiles.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.WAG;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class WAGTile extends GeneratorTile<WAG> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "wag";
    }

    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies", false);

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

}
