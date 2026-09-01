package adapters;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeAdapter;

/**
 * Wraps a BEAST Simplex in a Frequencies object.
 */
public final class FrequenciesAdapter implements TypeAdapter<Simplex, Frequencies, BEASTState> {

    @Override
    public Frequencies adapt(Simplex value, BEASTState state) {

        Frequencies frequencies = new Frequencies();

        state.setInput(frequencies, frequencies.frequenciesInput, value);

        return frequencies;
    }
}
