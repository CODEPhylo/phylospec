package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class K80Tile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "k80";
    }

    Input<RealScalar<PositiveReal>> kappaInput = new Input<>("kappa");

    @Override
    public HKY applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> kappa = this.kappaInput.apply(beastState);

        // k80 = hky with equal base frequencies
        SimplexParam equalFreqs = new SimplexParam(new double[]{0.25, 0.25, 0.25, 0.25});

        HKY hky = new HKY();
        hky.initByName("kappa", kappa, "frequencies", equalFreqs);

        return hky;
    }

    @Override
    protected Tile<?> createInstance() {
        return new K80Tile();
    }

}
