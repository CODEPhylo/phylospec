package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class HKYTile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "hky";
    }

    TileInput<RealScalar<PositiveReal>> kappaInput = new TileInput<>("kappa");
    TileInput<Simplex> baseFrequenciesInput = new TileInput<>("baseFrequencies");

    @Override
    public HKY applyTile(BEASTState beastState) {
        HKY hky = new HKY();

        RealScalar<PositiveReal> kappa = this.kappaInput.apply(beastState);
        Simplex baseFrequenciesInput = this.baseFrequenciesInput.apply(beastState);
        hky.initByName("kappa", kappa, "frequencies", baseFrequenciesInput);

        return hky;
    }

}
