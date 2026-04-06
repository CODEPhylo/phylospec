package tiles;

import tiles.functions.*;
import tiling.Tile;
import tiles.distributions.NormalTile;
import tiles.distributions.OffsetTile;
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
        addTile(new LogTile());
        addTile(new ExpTile());
        addTile(new SqrtTile());
        addTile(new LinSpaceTile());
        addTile(new RangeTile());
        addTile(new RepeatRealTile());
        addTile(new RepeatIntTile());

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
