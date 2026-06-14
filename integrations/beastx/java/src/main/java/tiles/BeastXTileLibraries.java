package tiles;

import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.List;

/**
 * Central loading point for BEAST X tile libraries.
 *
 * ServiceLoader is used when tile libraries are registered on the classpath.
 * The explicit fallback keeps local test runs robust even if service metadata
 * is missing or not copied into target/classes.
 */
public final class BeastXTileLibraries {

    private BeastXTileLibraries() {
    }

    public static List<CandidateTile<BeastXState>> loadAll() {
        List<CandidateTile<BeastXState>> tiles =
                new ArrayList<>(
                        TileLibrary.<BeastXState>loadAll()
                );

        if (tiles.isEmpty()) {
            tiles.addAll(
                    new BeastXCoreTileLibrary()
                            .getTiles()
            );
        }

        return tiles;
    }
}
