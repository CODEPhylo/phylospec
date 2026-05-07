package tiles;

import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import tiles.substitutionmodels.JC69Tile;
import tiles.substitutionmodels.K80Tile;
import tiles.substitutionmodels.F81Tile;
import tiles.substitutionmodels.HKYTile;
import tiles.substitutionmodels.GTRTile;
import tiling.BeastXState;

import java.util.ArrayList;
import java.util.List;

/**
 * This class loads all known tiles for BEAST X into a static field.
 */
public class BeastXCoreTileLibrary extends TileLibrary<BeastXState> {

    @Override
    public List<CandidateTile<BeastXState>> getTiles() {
        List<CandidateTile<BeastXState>> tiles = new ArrayList<>();

        tiles.add(new JC69Tile());
        tiles.add(new K80Tile());
        tiles.add(new F81Tile());
        tiles.add(new HKYTile());
        tiles.add(new GTRTile());
        return tiles;
    }
}
