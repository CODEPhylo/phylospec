package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class HKYTile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "hky";
    }

    Input<RealScalar<PositiveReal>> kappaInput = new Input<>("kappa");
    Input<Simplex> baseFrequenciesInput = new Input<>("baseFrequencies");

    @Override
    public HKY applyTile(BEASTState beastState) {
        HKY hky = new HKY();

        RealScalar<PositiveReal> kappa = this.kappaInput.apply(beastState);
        Simplex baseFrequenciesInput = this.baseFrequenciesInput.apply(beastState);
        hky.initByName("kappa", kappa, "frequencies", baseFrequenciesInput);

        return hky;
    }

    @Override
    protected Tile<?> createInstance() {
        return new HKYTile();
    }

}
