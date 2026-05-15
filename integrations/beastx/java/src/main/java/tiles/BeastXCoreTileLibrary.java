package tiles;

import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;

import tiles.misc.AssignmentTile;
import tiles.misc.AssignedArgumentTile;
import tiles.misc.LiteralTile;
import tiles.misc.DrawTile;
import tiles.distributions.ExponentialTile;
import tiles.distributions.LogNormalTile;
import tiles.distributions.NormalTile;
import tiles.distributions.GammaTile;
import tiles.distributions.BetaTile;
import tiles.distributions.UniformTile;
import tiles.distributions.CauchyTile;
import tiles.distributions.LogNormalRealSpaceTile;
import tiles.distributions.PoissonTile;
import tiles.functions.RepeatSimplexTile;

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

        // Basic PhyloSpec language support
        tiles.add(new AssignmentTile());
        tiles.add(new DrawTile());
        tiles.add(new LiteralTile<>());
        tiles.add(new AssignedArgumentTile());

        tiles.add(new RepeatSimplexTile());

        // BEAST X prior distributions
        tiles.add(new ExponentialTile());
        tiles.add(new LogNormalTile());
        tiles.add(new LogNormalRealSpaceTile());
        tiles.add(new NormalTile());
        tiles.add(new GammaTile());
        tiles.add(new BetaTile());
        tiles.add(new UniformTile());
        tiles.add(new CauchyTile());
        tiles.add(new PoissonTile());

        // BeastX substitution models
        tiles.add(new JC69Tile());
        tiles.add(new K80Tile());
        tiles.add(new F81Tile());
        tiles.add(new HKYTile());
        tiles.add(new GTRTile());
        return tiles;
    }
}
