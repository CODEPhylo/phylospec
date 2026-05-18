package tiles.substitutionmodels;

import dr.evomodel.substmodel.aminoacid.EmpiricalAminoAcidModel;
import dr.evomodel.substmodel.aminoacid.WAG;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WAGTileTest {

    @Test
    void appliesToProduceDefaultWAG() {
        WAGTile tile = new WAGTile();

        EmpiricalAminoAcidModel model =
                tile.applyTile(new BeastXState("test"), new IdentityHashMap<>());

        assertNotNull(model);
        assertEquals(WAG.INSTANCE, model.getEmpiricalRateMatrix());

        double[] frequencies =
                model.getFrequencyModel().getFrequencies();

        assertArrayEquals(
                WAG.INSTANCE.getEmpiricalFrequencies(),
                frequencies,
                1e-9
        );
    }

    @Test
    void appliesToProduceWAGWithCustomFrequencies() {
        WAGTile tile = new WAGTile();

        double[] expectedFrequencies = equalAminoAcidFrequencies();
        tile.baseFrequenciesInput.setTile(simplexTile(expectedFrequencies));

        EmpiricalAminoAcidModel model =
                tile.applyTile(new BeastXState("test"), new IdentityHashMap<>());

        assertNotNull(model);
        assertEquals(WAG.INSTANCE, model.getEmpiricalRateMatrix());

        double[] frequencies =
                model.getFrequencyModel().getFrequencies();

        assertArrayEquals(expectedFrequencies, frequencies, 1e-9);
    }

    @Test
    void generatorNameIsWAG() {
        assertEquals("wag", new WAGTile().getPhyloSpecGeneratorName());
    }

    private Tile<Simplex, BeastXState> simplexTile(double[] frequencies) {
        return new Tile<Simplex, BeastXState>() {
            @Override
            protected Simplex applyTile(
                    BeastXState state,
                    IdentityHashMap<Expr.Variable, Integer> indexVariables
            ) {
                return simplex(frequencies);
            }
        };
    }

    private Simplex simplex(double[] frequencies) {
        return new Simplex() {
            @Override
            public double get(int i) {
                return frequencies[i];
            }

            @Override
            public Double get(int... idx) {
                if (idx.length != 1) {
                    throw new IllegalArgumentException("Simplex requires exactly one index.");
                }
                return get(idx[0]);
            }

            @Override
            public List<UnitInterval> getElements() {
                return Collections.nCopies(frequencies.length, UnitInterval.INSTANCE);
            }

            @Override
            public long size() {
                return frequencies.length;
            }

            @Override
            public UnitInterval domainType() {
                return UnitInterval.INSTANCE;
            }
        };
    }

    private double[] equalAminoAcidFrequencies() {
        double[] frequencies = new double[20];

        for (int i = 0; i < frequencies.length; i++) {
            frequencies[i] = 0.05;
        }

        return frequencies;
    }
}
