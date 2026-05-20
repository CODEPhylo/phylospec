package tiles;

import org.phylospec.tiling.TileLibrary;
import org.phylospec.tiling.tiles.CandidateTile;

import tiles.misc.AssignmentTile;
import tiles.misc.AssignedArgumentTile;
import tiles.misc.LiteralTile;
import tiles.misc.DrawTile;
import tiles.misc.TreeDrawTile;
import tiles.misc.VectorTile;
import tiles.misc.DrawnArgumentTile;

import tiles.input.FromNexusTile;
import tiles.input.FromFastaTile;
import tiles.input.FromNewickTile;
import tiles.input.FromTreeTile;

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
import tiles.distributions.PhyloCTMCTile;

import tiles.functions.AlignmentTaxaTile;
import tiles.functions.RepeatSimplexTile;
import tiles.functions.RepeatRealTile;
import tiles.functions.RepeatIntTile;
import tiles.functions.LogTile;
import tiles.functions.ExpTile;
import tiles.functions.SqrtTile;
import tiles.functions.RangeTile;
import tiles.functions.NumVectorTile;
import tiles.functions.NumSitesTile;
import tiles.functions.NumTaxaAlignmentTile;
import tiles.functions.NumTaxaTreeTile;
import tiles.functions.LinSpaceTile;
import tiles.functions.NumBranchesTile;

import tiles.branchmodels.DrawnBranchRatesTile;
import tiles.branchmodels.StrictClockTile;

import tiles.observations.ObservedAsAlignmentTile;

import tiles.sitemodels.SiteModelTile;
import tiles.sitemodels.DrawnSiteRatesTile;

import tiles.substitutionmodels.JC69Tile;
import tiles.substitutionmodels.K80Tile;
import tiles.substitutionmodels.F81Tile;
import tiles.substitutionmodels.HKYTile;
import tiles.substitutionmodels.GTRTile;
import tiles.substitutionmodels.JTTTile;
import tiles.substitutionmodels.WAGTile;

import tiles.trees.YuleTile;
import tiles.trees.BirthDeathTile;
import tiles.trees.CoalescentTile;
import tiles.trees.ConstantPopulationFunctionTile;
import tiles.trees.CoalescentPopulationFunctionTile;

import tiling.BeastXState;

import java.util.ArrayList;
import java.util.List;

public class BeastXCoreTileLibrary extends TileLibrary<BeastXState> {

    @Override
    public List<CandidateTile<BeastXState>> getTiles() {
        List<CandidateTile<BeastXState>> tiles = new ArrayList<>();

        // Basic PhyloSpec language support
        tiles.add(new AssignmentTile());
        tiles.add(new DrawTile());
        tiles.add(new TreeDrawTile());
        tiles.add(new ObservedAsAlignmentTile());
        tiles.add(new LiteralTile<>());
        tiles.add(new VectorTile<>());
        tiles.add(new AssignedArgumentTile());
        tiles.add(new DrawnArgumentTile());

        // Input/accessor support
        tiles.add(new FromNexusTile());
        tiles.add(new FromFastaTile());
        tiles.add(new FromNewickTile());
        tiles.add(new FromTreeTile());
        tiles.add(new AlignmentTaxaTile());

        // PhyloSpec functions
        tiles.add(new RepeatSimplexTile());
        tiles.add(new RepeatRealTile());
        tiles.add(new RepeatIntTile());
        tiles.add(new LogTile());
        tiles.add(new ExpTile());
        tiles.add(new SqrtTile());
        tiles.add(new RangeTile());
        tiles.add(new NumVectorTile());
        tiles.add(new NumSitesTile());
        tiles.add(new NumTaxaAlignmentTile());
        tiles.add(new NumTaxaTreeTile());
        tiles.add(new NumBranchesTile());
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

        // BEAST X tree priors
        tiles.add(new YuleTile());
        tiles.add(new BirthDeathTile());
        tiles.add(new CoalescentTile());
        tiles.add(new ConstantPopulationFunctionTile());
        tiles.add(new CoalescentPopulationFunctionTile());

        // BEAST X branch models
        tiles.add(new DrawnBranchRatesTile());
        tiles.add(new StrictClockTile());

        // BEAST X sequence likelihoods
        tiles.add(new PhyloCTMCTile());

        // BEAST X substitution models
        tiles.add(new JC69Tile());
        tiles.add(new K80Tile());
        tiles.add(new F81Tile());
        tiles.add(new HKYTile());
        tiles.add(new GTRTile());
        tiles.add(new JTTTile());
        tiles.add(new WAGTile());

        // BEAST X site models
        tiles.add(new DrawnSiteRatesTile());
        tiles.add(new SiteModelTile());

        return tiles;
    }
}