package tiles;

import patternmatching.Tile;

import java.util.ArrayList;
import java.util.List;

public class TileLibrary {
    private static List<Tile> tiles = new ArrayList<>();

    static {
        addTile(new AssignmentTile());
        addTile(new StateNodeAssignmentTile());
        addTile(new DrawTile());
        addTile(new LiteralTile());

        addTile(new EnvTile());

        addTile(new NormalTile());
    }

    public static void addTile(Tile tile) {
        tiles.add(tile);
    }

    public static List<Tile> getTiles() {
        return tiles;
    }
}
