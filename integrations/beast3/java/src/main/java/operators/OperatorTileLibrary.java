package operators;

import tiles.distributions.*;
import tiles.functions.*;
import tiles.input.*;
import tiles.misc.*;
import tiles.substitutionmodels.*;
import tiles.trees.*;
import tiling.Tile;

import java.util.ArrayList;
import java.util.List;

public class OperatorTileLibrary {
    private final static List<Tile<?>> tiles = new ArrayList<>();

    static {
        addTile(new BranchRateTreeUpDownOperatorTile());
    }

    public static void addTile(Tile<?> tile) {
        tiles.add(tile);
    }

    public static List<Tile<?>> getTiles() {
        return tiles;
    }
}
