package tiles.substitutionModels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class F81Tile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "f81";
    }

    Input<Simplex> baseFrequenciesInput = new Input<>("baseFrequencies");

    @Override
    public HKY applyTile(BEASTState beastState) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        // f81 = hky with kappa = 1 (equal transition/transversion rates)
        RealScalarParam<PositiveReal> kappaOne = new RealScalarParam<>(1.0, PositiveReal.INSTANCE);

        HKY hky = new HKY();
        hky.initByName("kappa", kappaOne, "frequencies", baseFrequencies);

        return hky;
    }

    @Override
    protected Tile<?> createInstance() {
        return new F81Tile();
    }

}
