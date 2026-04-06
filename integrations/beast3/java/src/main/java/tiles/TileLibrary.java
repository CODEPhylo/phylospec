package tiles;

import tiling.Tile;
import tiles.distributions.NormalTile;
import tiles.distributions.OffsetTile;
import tiles.functions.EnvTile;
import tiles.misc.AssignmentTile;
import tiles.misc.DrawTile;
import tiles.misc.LiteralTile;
import tiles.misc.StateNodeAssignmentTile;

import java.util.ArrayList;
import java.util.List;

public class TileLibrary {
    private static List<Tile<?>> tiles = new ArrayList<>();

    static {
        addTile(new AssignmentTile());
        addTile(new StateNodeAssignmentTile());
        addTile(new DrawTile());
        addTile(new LiteralTile<>());

        addTile(new EnvTile());

        addTile(new OffsetTile());
        addTile(new NormalTile());
    }

    public static void addTile(Tile<?> tile) {
        tiles.add(tile);
    }

    public static List<Tile<?>> getTiles() {
        return tiles;
    }
}
