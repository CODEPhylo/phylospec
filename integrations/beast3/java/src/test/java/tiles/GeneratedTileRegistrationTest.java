package tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.substitutionmodels.HKYGeneratedTile;
import tiles.substitutionmodels.JC69GeneratedTile;
import tiles.substitutionmodels.WAGGeneratedTile;
import tiles.trees.ConstantPopulationGeneratedTile;
import tiles.trees.ExponentialPopulationGeneratedTile;

public class GeneratedTileRegistrationTest {

    @Test
    public void registersGeneratedTilesExactlyOnce() {
        List<CandidateTile<BEASTState>> tiles = new BeastCoreTileLibrary().getTiles();

        assertGeneratedTile(tiles, "jc69", JC69GeneratedTile.class);

        assertGeneratedTile(tiles, "constantPopulationFunction", ConstantPopulationGeneratedTile.class);

        assertGeneratedTile(tiles, "exponentialPopulationFunction", ExponentialPopulationGeneratedTile.class);

        assertGeneratedTile(tiles, "hky", HKYGeneratedTile.class);

        assertGeneratedTile(tiles, "wag", WAGGeneratedTile.class);
    }

    @Test
    public void discoversGeneratedTilesThroughServiceLoader() {
        List<CandidateTile<BEASTState>> tiles = TileLibrary.loadAll(BEASTState.class);

        assertGeneratedTile(tiles, "jc69", JC69GeneratedTile.class);

        assertGeneratedTile(tiles, "constantPopulationFunction", ConstantPopulationGeneratedTile.class);

        assertGeneratedTile(tiles, "exponentialPopulationFunction", ExponentialPopulationGeneratedTile.class);

        assertGeneratedTile(tiles, "hky", HKYGeneratedTile.class);

        assertGeneratedTile(tiles, "wag", WAGGeneratedTile.class);
    }

    private void assertGeneratedTile(
            List<CandidateTile<BEASTState>> tiles, String componentName, Class<?> expectedClass) {

        List<CandidateTile<BEASTState>> matchingTiles =
                tiles.stream().filter(tile -> isGenerator(tile, componentName)).toList();

        assertEquals(1, matchingTiles.size(), "Expected exactly one registered Tile for '" + componentName + "'.");

        assertInstanceOf(expectedClass, matchingTiles.getFirst());
    }

    private boolean isGenerator(CandidateTile<BEASTState> candidateTile, String componentName) {

        return candidateTile instanceof GeneratorTile<?, ?> generatorTile
                && componentName.equals(generatorTile.getPhyloSpecGeneratorName());
    }
}
