package tiles;

import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;

import tiles.misc.AssignmentTile;
import tiles.misc.AssignedArgumentTile;
import tiles.misc.LiteralTile;
import tiles.misc.DrawTile;
import tiles.misc.VectorTile;

import tiles.distributions.ExponentialTile;
import tiles.distributions.LogNormalTile;
import tiles.distributions.NormalTile;
import tiles.distributions.GammaTile;
import tiles.distributions.BetaTile;
import tiles.distributions.UniformTile;
import tiles.distributions.CauchyTile;
import tiles.distributions.LogNormalRealSpaceTile;
import tiles.distributions.PoissonTile;
import tiles.distributions.DirichletTile;
import tiles.distributions.DiscreteUniformTile;

import tiles.functions.RepeatSimplexTile;
import tiles.functions.RepeatRealTile;
import tiles.functions.RepeatIntTile;
import tiles.functions.LogTile;
import tiles.functions.ExpTile;
import tiles.functions.SqrtTile;
import tiles.functions.RangeTile;
import tiles.functions.NumVectorTile;
import tiles.functions.LinSpaceTile;

import tiles.sitemodels.SiteModelTile;
import tiles.sitemodels.DrawnSiteRatesTile;

import tiles.substitutionmodels.JC69Tile;
import tiles.substitutionmodels.K80Tile;
import tiles.substitutionmodels.F81Tile;
import tiles.substitutionmodels.HKYTile;
import tiles.substitutionmodels.GTRTile;
import tiles.substitutionmodels.JTTTile;
import tiles.substitutionmodels.WAGTile;
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
        tiles.add(new VectorTile<>());
        tiles.add(new AssignedArgumentTile());

        tiles.add(new RepeatSimplexTile());
        tiles.add(new RepeatRealTile());
        tiles.add(new RepeatIntTile());
        tiles.add(new LogTile());
        tiles.add(new ExpTile());
        tiles.add(new SqrtTile());
        tiles.add(new RangeTile());
        tiles.add(new NumVectorTile());
        tiles.add(new LinSpaceTile());

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
        tiles.add(new DiscreteUniformTile());
        tiles.add(new DirichletTile());

        // BeastX substitution models
        tiles.add(new JC69Tile());
        tiles.add(new K80Tile());
        tiles.add(new F81Tile());
        tiles.add(new HKYTile());
        tiles.add(new GTRTile());
        tiles.add(new JTTTile());
        tiles.add(new WAGTile());

        // BeastX site models
        tiles.add(new DrawnSiteRatesTile());
        tiles.add(new SiteModelTile());
        return tiles;
    }
}
