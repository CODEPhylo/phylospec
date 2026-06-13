package tiles;

import tiles.operators.BranchRateTreeUpDownOperatorTile;
import tiling.Tile;

import java.util.ArrayList;
import java.util.List;

/**
 * This class loads all known operator tiles into a static field.
 */
public class OperatorTileLibrary {
    private final static List<Tile<?>> tiles = new ArrayList<>();

    static {
        // addTile(new BranchRateTreeUpDownOperatorTile());
    }

    public static void addTile(Tile<?> tile) {
        tiles.add(tile);
    }

    public static List<Tile<?>> getTiles() {
        return tiles;
    }
}
