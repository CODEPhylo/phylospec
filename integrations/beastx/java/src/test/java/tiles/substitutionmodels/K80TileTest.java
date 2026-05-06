package tiles.substitutionmodels;

import dr.evomodel.substmodel.nucleotide.HKY;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.types.RealParamImpl;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;

import java.util.IdentityHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class K80TileTest {

    @Test
    void appliesToProduceK80ConfiguredHKY() {
        K80Tile tile = new K80Tile();

        tile.kappaInput.setTile(new Tile<RealScalar<PositiveReal>, BeastXState>() {
            @Override
            protected RealScalar<PositiveReal> applyTile(
                    BeastXState state,
                    IdentityHashMap<Expr.Variable, Integer> indexVariables
            ) {
                return new RealParamImpl<>(2.0, PositiveReal.INSTANCE);
            }
        });

        HKY model = tile.applyTile(new BeastXState("test"), new IdentityHashMap<>());

        assertNotNull(model);
        assertEquals(2.0, model.getKappa(), 1e-9);

        double[] freqs = model.getFrequencyModel().getFrequencies();
        assertEquals(4, freqs.length);

        for (double f : freqs) {
            assertEquals(0.25, f, 1e-9);
        }
    }

    @Test
    void generatorNameIsK80() {
        assertEquals("k80", new K80Tile().getPhyloSpecGeneratorName());
    }
}