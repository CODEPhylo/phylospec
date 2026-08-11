package tiles;

import beastconfig.BEASTState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.tiling.tiles.CandidateTile;
import tiles.misc.LiteralTile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastTileLibrariesTest {

    @Test
    public void discoversSampledAncestorsTiles() {
        assertTrue(
                BeastTileLibraries.loadAll().stream()
                        .anyMatch(
                                tile ->
                                        tile.getClass()
                                                .getSimpleName()
                                                .equals("FossilizedBirthDeathTile")));
    }

    @Test
    public void includesTilesFromAvailablePackages() {
        int coreTileCount = new BeastCoreTileLibrary().getTiles().size();
        BeastPackageTileLibrary packageLibrary = packageLibrary(true);

        assertEquals(
                coreTileCount + 1,
                BeastTileLibraries.loadAll(List.of(packageLibrary)).size());
    }

    @Test
    public void ignoresTilesFromUnavailablePackages() {
        int coreTileCount = new BeastCoreTileLibrary().getTiles().size();
        BeastPackageTileLibrary packageLibrary = packageLibrary(false);

        assertEquals(
                coreTileCount,
                BeastTileLibraries.loadAll(List.of(packageLibrary)).size());
    }

    private static BeastPackageTileLibrary packageLibrary(boolean available) {
        return new BeastPackageTileLibrary() {
            @Override
            public String getPackageId() {
                return "test-package";
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public List<CandidateTile<BEASTState>> getTiles() {
                return List.of(new LiteralTile<>());
            }
        };
    }
}
