package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
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

    GeneratorTileInput<RealScalar<PositiveReal>> kappaInput = new GeneratorTileInput<>("kappa");
    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies");

    @Override
    public HKY applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> kappa = this.kappaInput.apply(beastState);
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState);

        // initialize frequencies

        Frequencies frequencies = new Frequencies();
        beastState.setInput(frequencies, frequencies.frequenciesInput, baseFrequencies);

        // initialize HKY

        HKY hky = new HKY();
        beastState.setInput(hky, hky.kappaInput, kappa);
        beastState.setInput(hky, hky.frequenciesInput, frequencies);

        return hky;
    }

}
