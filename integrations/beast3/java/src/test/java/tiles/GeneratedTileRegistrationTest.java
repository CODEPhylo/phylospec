package tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.substitutionmodels.GTRGeneratedTile;
import tiles.substitutionmodels.GTRRelativeRatesTile;
import tiles.substitutionmodels.HKYGeneratedTile;
import tiles.substitutionmodels.JC69GeneratedTile;
import tiles.substitutionmodels.JTTGeneratedTile;
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

        assertGeneratedTile(tiles, "jtt", JTTGeneratedTile.class);

        assertGtrOverloads(tiles);
    }

    @Test
    public void discoversGeneratedTilesThroughServiceLoader() {
        List<CandidateTile<BEASTState>> tiles = TileLibrary.loadAll(BEASTState.class);

        assertGeneratedTile(tiles, "jc69", JC69GeneratedTile.class);

        assertGeneratedTile(tiles, "constantPopulationFunction", ConstantPopulationGeneratedTile.class);

        assertGeneratedTile(tiles, "exponentialPopulationFunction", ExponentialPopulationGeneratedTile.class);

        assertGeneratedTile(tiles, "hky", HKYGeneratedTile.class);

        assertGeneratedTile(tiles, "wag", WAGGeneratedTile.class);

        assertGeneratedTile(tiles, "jtt", JTTGeneratedTile.class);

        assertGtrOverloads(tiles);
    }

    private void assertGeneratedTile(
            List<CandidateTile<BEASTState>> tiles, String componentName, Class<?> expectedClass) {

        List<CandidateTile<BEASTState>> matchingTiles =
                tiles.stream().filter(tile -> isGenerator(tile, componentName)).toList();

        assertEquals(1, matchingTiles.size(), "Expected exactly one registered Tile for '" + componentName + "'.");

        assertInstanceOf(expectedClass, matchingTiles.getFirst());
    }

    private void assertGtrOverloads(List<CandidateTile<BEASTState>> tiles) {

        List<CandidateTile<BEASTState>> gtrTiles =
                tiles.stream().filter(tile -> isGenerator(tile, "gtr")).toList();

        assertEquals(2, gtrTiles.size(), "Expected exactly two registered GTR overloads.");

        assertEquals(1, countInstances(gtrTiles, GTRGeneratedTile.class), "Expected one generated ordinary GTR Tile.");

        assertEquals(
                1,
                countInstances(gtrTiles, GTRRelativeRatesTile.class),
                "Expected one handwritten relative-rates GTR Tile.");
    }

    private long countInstances(List<CandidateTile<BEASTState>> tiles, Class<?> expectedClass) {

        return tiles.stream().filter(expectedClass::isInstance).count();
    }

    private boolean isGenerator(CandidateTile<BEASTState> candidateTile, String componentName) {

        return candidateTile instanceof GeneratorTile<?, ?> generatorTile
                && componentName.equals(generatorTile.getPhyloSpecGeneratorName());
    }
}
