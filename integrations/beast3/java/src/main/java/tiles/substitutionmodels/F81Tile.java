package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

public class F81Tile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "f81";
    }

    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies");

    @Override
    public HKY applyTile(BEASTState beastState) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        // f81 = hky with kappa = 1 (equal transition/transversion rates)
        RealScalarParam<PositiveReal> kappaOne = new RealScalarParam<>(1.0, PositiveReal.INSTANCE);

        // initialize frequencies

        Frequencies frequencies = new Frequencies();
        beastState.setInput(frequencies, frequencies.frequenciesInput, baseFrequencies);

        // initialize HKY

        HKY hky = new HKY();
        beastState.setInput(hky, hky.kappaInput, kappaOne);
        beastState.setInput(hky, hky.frequenciesInput, frequencies);

        return hky;
    }

}
