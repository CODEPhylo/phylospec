package tiles.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.JTT;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.Map;

public class JTTTile extends GeneratorTile<JTT> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "jtt";
    }

    GeneratorTileInput<Simplex> baseFrequenciesInput = new GeneratorTileInput<>("baseFrequencies", false);

    @Override
    public JTT applyTile(BEASTState beastState, Map<String, Integer> indexVariables) {
        Simplex baseFrequencies = this.baseFrequenciesInput.apply(beastState, indexVariables);

        JTT jtt = new JTT();
        if (baseFrequencies != null) {

            // initialize frequencies

            Frequencies frequencies = new Frequencies();
            beastState.setInput(frequencies, frequencies.frequenciesInput, baseFrequencies);
            beastState.setInput(jtt, jtt.frequenciesInput, frequencies);

        } else {
            jtt.initAndValidate();
        }

        return jtt;
    }

}
