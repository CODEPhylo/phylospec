package tiles.substitutionmodels;

import dr.evomodel.substmodel.nucleotide.HKY;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class F81TileTest {

    @Test
    void appliesToProduceF81ConfiguredHKY() {
        F81Tile tile = new F81Tile();

        double[] expectedFrequencies = new double[]{0.1, 0.2, 0.3, 0.4};

        tile.baseFrequenciesInput.setTile(new Tile<Simplex, BeastXState>() {
            @Override
            protected Simplex applyTile(
                    BeastXState state,
                    IdentityHashMap<Expr.Variable, Integer> indexVariables
            ) {
                return new Simplex() {
                    @Override
                    public double get(int i) {
                        return expectedFrequencies[i];
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
                        return List.of(
                                UnitInterval.INSTANCE,
                                UnitInterval.INSTANCE,
                                UnitInterval.INSTANCE,
                                UnitInterval.INSTANCE
                        );
                    }

                    @Override
                    public long size() {
                        return expectedFrequencies.length;
                    }

                    @Override
                    public UnitInterval domainType() {
                        return UnitInterval.INSTANCE;
                    }
                };
            }
        });

        HKY model = tile.applyTile(new BeastXState("test"), new IdentityHashMap<>());

        assertNotNull(model);
        assertEquals(1.0, model.getKappa(), 1e-9);

        double[] freqs = model.getFrequencyModel().getFrequencies();
        assertEquals(4, freqs.length);

        for (int i = 0; i < freqs.length; i++) {
            assertEquals(expectedFrequencies[i], freqs[i], 1e-9);
        }
    }

    @Test
    void generatorNameIsF81() {
        assertEquals("f81", new F81Tile().getPhyloSpecGeneratorName());
    }
}