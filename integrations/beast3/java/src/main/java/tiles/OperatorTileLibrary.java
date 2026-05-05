package tiles;

import beastconfig.BEASTState;
import org.phylospec.tiling.tiles.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * This class loads all known operator tiles into a static field.
 */
public class OperatorTileLibrary {
    private final static List<Tile<?, BEASTState>> tiles = new ArrayList<>();

    static {
        // addTile(new BranchRateTreeUpDownOperatorTile());
    }

    public static void addTile(Tile<?, BEASTState> tile) {
        tiles.add(tile);
    }

    public static List<Tile<?, BEASTState>> getTiles() {
        return tiles;
    }
}
