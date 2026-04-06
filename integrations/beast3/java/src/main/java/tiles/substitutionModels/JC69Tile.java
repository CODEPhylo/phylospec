package tiles.substitutionModels;

import beast.base.spec.evolution.substitutionmodel.JukesCantor;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class JC69Tile extends GeneratorTile<JukesCantor> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "jc69";
    }

    @Override
    public JukesCantor applyTile(BEASTState beastState) {
        return new JukesCantor();
    }

    @Override
    protected Tile<?> createInstance() {
        return new JC69Tile();
    }

}
