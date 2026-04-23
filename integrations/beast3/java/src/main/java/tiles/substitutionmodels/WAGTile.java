package tiles.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.WAG;
import beast.base.spec.type.Simplex;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class WAGTile extends GeneratorTile<WAG> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "wag";
    }

    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies", false);

    @Override
    public WAG applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

        WAG wag = new WAG();
        if (baseFrequencies != null) {

            // initialize frequencies

            Frequencies frequencies = new Frequencies();
            beastState.setInput(frequencies, frequencies.frequenciesInput, baseFrequencies);
            beastState.setInput(wag, wag.frequenciesInput, frequencies);

        } else {
            wag.initAndValidate();
        }

        return wag;
    }

}
