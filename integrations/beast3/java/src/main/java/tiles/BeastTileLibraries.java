package tiles;

import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.phylospec.tiling.tiles.CandidateTile;

/** Loads the built-in BEAST tiles and tiles contributed by installed package integrations. */
public final class BeastTileLibraries {

    private BeastTileLibraries() {}

    public static List<CandidateTile<BEASTState>> loadAll() {
        return loadAll(ServiceLoader.load(BeastPackageTileLibrary.class));
    }

    static List<CandidateTile<BEASTState>> loadAll(
            Iterable<BeastPackageTileLibrary> packageLibraries) {
        List<CandidateTile<BEASTState>> tiles =
                new ArrayList<>(new BeastCoreTileLibrary().getTiles());

        for (BeastPackageTileLibrary library : packageLibraries) {
            if (library.isAvailable()) {
                tiles.addAll(library.getTiles());
            }
        }

        return tiles;
    }
}
