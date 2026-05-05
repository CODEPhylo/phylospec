package tiles;

import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;
import tiles.substitutionmodels.JC69Tile;
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

        return tiles;
    }
}
