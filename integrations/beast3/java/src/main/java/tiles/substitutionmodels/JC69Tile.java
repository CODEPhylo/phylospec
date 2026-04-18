package tiles.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

public class JC69Tile extends GeneratorTile<JukesCantor> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "jc69";
    }

    @Override
    public JukesCantor applyTile(BEASTState beastState) {
        return new JukesCantor();
    }

}
