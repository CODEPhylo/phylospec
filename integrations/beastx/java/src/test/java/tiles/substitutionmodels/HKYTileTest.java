package tiles.substitutionmodels;

import dr.evomodel.substmodel.nucleotide.HKY;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.types.RealParamImpl;
import org.phylospec.types.RealScalar;
import org.phylospec.types.Simplex;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HKYTileTest {

    @Test
    void appliesToProduceHKYConfiguredHKY() {
        HKYTile tile = new HKYTile();

        double expectedKappa = 3.0;
        double[] expectedFrequencies = new double[]{0.3, 0.2, 0.2, 0.3};

        tile.kappaInput.setTile(realScalarTile(expectedKappa));
        tile.baseFrequenciesInput.setTile(simplexTile(expectedFrequencies));

        HKY model = tile.applyTile(new BeastXState("test"), new IdentityHashMap<>());

        assertNotNull(model);
        assertEquals(expectedKappa, model.getKappa(), 1e-9);

        double[] freqs = model.getFrequencyModel().getFrequencies();
        assertEquals(4, freqs.length);

        for (int i = 0; i < freqs.length; i++) {
            assertEquals(expectedFrequencies[i], freqs[i], 1e-9);
        }
    }

    @Test
    void generatorNameIsHKY() {
        assertEquals("hky", new HKYTile().getPhyloSpecGeneratorName());
    }

    private Tile<RealScalar<PositiveReal>, BeastXState> realScalarTile(double value) {
        return new Tile<RealScalar<PositiveReal>, BeastXState>() {
            @Override
            protected RealScalar<PositiveReal> applyTile(
                    BeastXState state,
                    IdentityHashMap<Expr.Variable, Integer> indexVariables
            ) {
                return new RealParamImpl<>(value, PositiveReal.INSTANCE);
            }
        };
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
                return List.of(
                        UnitInterval.INSTANCE,
                        UnitInterval.INSTANCE,
                        UnitInterval.INSTANCE,
                        UnitInterval.INSTANCE
                );
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
}
