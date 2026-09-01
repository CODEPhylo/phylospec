package adapters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.spec.evolution.substitutionmodel.Frequencies;
import beast.base.spec.evolution.substitutionmodel.WAG;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.Simplex;
import beastconfig.BEASTState;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.phylospec.tiling.TypeAdapter;

public class FrequenciesAdapterTest {

    @Test
    public void convertsSimplexToFrequencies() {
        double[] values = new double[20];

        Arrays.fill(values, 1.0 / values.length);

        Simplex simplex = new SimplexParam(values);

        BEASTState state = new BEASTState("frequencies-adapter");

        TypeAdapter<Simplex, Frequencies, BEASTState> adapter = new FrequenciesAdapter();

        Frequencies frequencies = adapter.adapt(simplex, state);

        assertSame(simplex, frequencies.frequenciesInput.get());

        WAG wag = new WAG();

        state.setInput(wag, wag.frequenciesInput, frequencies);

        assertSame(frequencies, wag.frequenciesInput.get());

        assertArrayEquals(values, frequencies.getFreqs(), 1.0e-12);
    }
}
