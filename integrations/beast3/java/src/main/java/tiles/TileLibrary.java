package tiles;

import tiles.branchmodels.RelaxedClockTile;
import tiles.branchmodels.StrictClockTile;
import tiles.functions.*;
import tiles.sitemodels.SiteModelTile;
import tiles.trees.YuleTile;
import tiling.Tile;
import tiles.distributions.*;
import tiles.misc.*;
import tiles.substitutionmodels.*;

import java.util.ArrayList;
import java.util.List;

public class TileLibrary {
    private final static List<Tile<?>> tiles = new ArrayList<>();

    static {
        addTile(new AssignmentTile());
        addTile(new StateNodeAssignmentTile());
        addTile(new DrawTile());
        addTile(new LiteralTile<>());
        addTile(new ObservedAsTile());
        addTile(new DrawnArgumentTile());
        addTile(new AssignedArgumentTile());

        addTile(new EnvTile());
        addTile(new LogTile());
        addTile(new ExpTile());
        addTile(new SqrtTile());
        addTile(new LinSpaceTile());
        addTile(new RangeTile());
        addTile(new RepeatRealTile());
        addTile(new RepeatIntTile());

        addTile(new AlignmentTaxaTile());
        addTile(new TreeTaxaTile());

        addTile(new FromNexusTile());
        addTile(new ParserTile.Regex());
        addTile(new ParserTile.Delimiter());

        addTile(new OffsetTile());
        addTile(new NormalTile());
        addTile(new LogNormalTile());
        addTile(new LogNormalRealSpaceTile());

        addTile(new YuleTile());

        addTile(new StrictClockTile());
        addTile(new RelaxedClockTile());

        addTile(new JC69Tile());
        addTile(new K80Tile());
        addTile(new F81Tile());
        addTile(new HKYTile());
        addTile(new GTRTile());
        addTile(new WAGTile());
        addTile(new JTTTile());

        addTile(new SiteModelTile());
        addTile(new PhyloCTMCTile());
    }

    public static void addTile(Tile<?> tile) {
        tiles.add(tile);
    }

    public static List<Tile<?>> getTiles() {
        return tiles;
    }
}
