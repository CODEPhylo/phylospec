package tiles.substitutionmodels;

import dr.evomodel.substmodel.nucleotide.HKY;
import org.junit.jupiter.api.Test;
import tiling.BeastXState;

import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JC69TileTest {

    @Test
    void appliesToProduceJC69ConfiguredHKY() {
        JC69Tile tile = new JC69Tile();
        HKY model = tile.applyTile(new BeastXState("test"), new IdentityHashMap<>());

        assertNotNull(model);
        assertEquals(1.0, model.getKappa(), 1e-9);
        double[] freqs = model.getFrequencyModel().getFrequencies();
        assertEquals(4, freqs.length);
        for (double f : freqs) {
            assertEquals(0.25, f, 1e-9);
        }
    }

    @Test
    void generatorNameIsJc69() {
        assertEquals("jc69", new JC69Tile().getPhyloSpecGeneratorName());
    }
}
