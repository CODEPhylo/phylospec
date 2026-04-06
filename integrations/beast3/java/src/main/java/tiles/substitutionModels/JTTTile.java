package tiles.substitutionModels;

import beast.base.spec.evolution.substitutionmodel.JTT;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class JTTTile extends GeneratorTile<JTT> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "jtt";
    }

    Input<Simplex> baseFrequenciesInput = new Input<>("baseFrequencies", false);

    @Override
    public JTT applyTile(BEASTState beastState) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        JTT jtt = new JTT();
        if (baseFrequencies != null) {
            jtt.initByName("frequencies", baseFrequencies);
        } else {
            jtt.initAndValidate();
        }

        return jtt;
    }

    @Override
    protected Tile<?> createInstance() {
        return new JTTTile();
    }

}
