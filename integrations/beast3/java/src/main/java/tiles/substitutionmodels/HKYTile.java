package tiles.substitutionmodels;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class HKYTile extends GeneratorTile<HKY> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "hky";
    }

    GeneratorTileInput<RealScalar<PositiveReal>> kappaInput = new GeneratorTileInput<>("kappa");
    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies");

    @Override
    public HKY applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> kappa = this.kappaInput.apply(beastState, indexVariables);
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

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
