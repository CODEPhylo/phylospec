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
import tiles.misc.ListVectorTile;
import tiles.misc.IndexVariableTile;
import tiles.misc.IndexedTile;
import tiles.misc.IndexedStatementTile;

import tiles.input.FromNexusTile;
import tiles.input.FromFastaTile;
import tiles.input.FromNewickTile;
import tiles.input.FromTreeTile;
import tiles.input.ParserTile;
import tiles.input.SubsetTile;
import tiles.input.FromCSVTile;

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
import tiles.distributions.OffsetTile;
import tiles.distributions.BinomialTile;
import tiles.distributions.BernoulliTile;
import tiles.distributions.MultivariateNormalTile;
import tiles.distributions.CategoricalTile;
import tiles.distributions.GeometricTile;
import tiles.distributions.IIDTile;
import tiles.distributions.TruncatedTile;
import tiles.distributions.PhyloBMTile;
import tiles.distributions.PhyloOUTile;

import tiles.errors.VectorBranchRatesErrorTile;
import tiles.errors.VectorSiteRatesErrorTile;

import tiles.functions.AlignmentTaxaTile;
import tiles.functions.RepeatSimplexTile;
import tiles.functions.RepeatRealTile;
import tiles.functions.RepeatIntTile;
import tiles.functions.LogTile;
import tiles.functions.ExpTile;
import tiles.functions.SqrtTile;
import tiles.functions.EnvTile;
import tiles.functions.RangeTile;
import tiles.functions.NumVectorTile;
import tiles.functions.NumListTile;
import tiles.functions.NumSitesTile;
import tiles.functions.NumTaxaAlignmentTile;
import tiles.functions.NumTaxaTreeTile;
import tiles.functions.LinSpaceTile;
import tiles.functions.NumBranchesTile;
import tiles.functions.RootAgeTile;
import tiles.functions.TaxonTile;
import tiles.functions.AgeTaxonTile;
import tiles.functions.AgeNodeTreeTile;
import tiles.functions.MRCATile;
import tiles.functions.TreeTaxaTile;
import tiles.functions.NumRowsTile;
import tiles.functions.NumColsTile;
import tiles.functions.SumRealVectorTile;

import tiles.branchmodels.DrawnBranchRatesTile;
import tiles.branchmodels.StrictClockTile;
import tiles.branchmodels.RelaxedClockTile;

import tiles.observations.ObservedAsAlignmentTile;
import tiles.observations.RootObservedBetweenTile;
import tiles.observations.MRCAObservedBetweenTile;
import tiles.observations.ObservedAsTile;
import tiles.observations.ObservedAsTreeTile;
import tiles.observations.ObservedAsNonNegativeIntTile;
import tiles.observations.ObservedAsIntTile;

import tiles.rpn.BinaryTile;
import tiles.rpn.ExpRPNTile;
import tiles.rpn.LogRPNTile;
import tiles.rpn.RPNAssignmentTile;
import tiles.rpn.SqrtRPNTile;
import tiles.rpn.UnaryRPNTile;

import tiles.mcmc.AutoOperatorConfigTile;
import tiles.mcmc.ChainLengthTile;
import tiles.mcmc.DefaultLogEveryTile;
import tiles.mcmc.OutputPrefixTile;
import tiles.mcmc.ScreenLoggerTile;
import tiles.mcmc.FileLoggerTile;
import tiles.mcmc.TreeLoggerTile;
import tiles.mcmc.RandomSeedTile;

import tiles.sitemodels.SiteModelTile;
import tiles.sitemodels.DrawnSiteRatesTile;

import tiles.substitutionmodels.JC69Tile;
import tiles.substitutionmodels.K80Tile;
import tiles.substitutionmodels.F81Tile;
import tiles.substitutionmodels.HKYTile;
import tiles.substitutionmodels.GTRTile;
import tiles.substitutionmodels.GTRRelativeRatesTile;
import tiles.substitutionmodels.JTTTile;
import tiles.substitutionmodels.WAGTile;
import tiles.substitutionmodels.LGTile;
import tiles.substitutionmodels.GY94Tile;
import tiles.substitutionmodels.MkTile;

import tiles.trees.YuleTile;
import tiles.trees.BirthDeathTile;
import tiles.trees.CoalescentTile;
import tiles.trees.ConstantPopulationFunctionTile;
import tiles.trees.ExponentialPopulationFunctionTile;
import tiles.trees.CoalescentPopulationFunctionTile;
import tiles.trees.LogisticPopulationFunctionTile;
import tiles.trees.CompoundPopulationFunctionTile;
import tiles.trees.SkylineCoalescentTile;
import tiles.trees.FossilizedBirthDeathTile;

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
        tiles.add(new ObservedAsTreeTile());
        tiles.add(new ObservedAsTile());
        tiles.add(new ObservedAsNonNegativeIntTile());
        tiles.add(new ObservedAsIntTile());
        tiles.add(new RootObservedBetweenTile());
        tiles.add(new MRCAObservedBetweenTile());
        tiles.add(new LiteralTile<>());
        tiles.add(new VectorTile<>());
        tiles.add(new AssignedArgumentTile());
        tiles.add(new DrawnArgumentTile());
        tiles.add(new ListVectorTile());
        tiles.add(new tiles.misc.RangeTile());
        tiles.add(new IndexVariableTile());
        tiles.add(new IndexedTile());
        tiles.add(new IndexedStatementTile());

        // RPN deterministic calculations
        tiles.add(new BinaryTile.RpnRpn());
        tiles.add(new BinaryTile.RpnReal());
        tiles.add(new BinaryTile.RealRpn());
        tiles.add(new BinaryTile.RealReal());
        tiles.add(new UnaryRPNTile.Rpn());
        tiles.add(new UnaryRPNTile.RealScalarInput());
        tiles.add(new ExpRPNTile.Rpn());
        tiles.add(new ExpRPNTile.Real());
        tiles.add(new LogRPNTile.Rpn());
        tiles.add(new LogRPNTile.Real());
        tiles.add(new SqrtRPNTile());
        tiles.add(new RPNAssignmentTile());

        // Input/accessor support
        tiles.add(new FromNexusTile());
        tiles.add(new FromFastaTile());
        tiles.add(new FromCSVTile());
        tiles.add(new FromNewickTile());
        tiles.add(new FromTreeTile());
        tiles.add(new SubsetTile());
        tiles.add(new ParserTile.Regex());
        tiles.add(new ParserTile.Delimiter());

        // PhyloSpec functions
        tiles.add(new RepeatSimplexTile());
        tiles.add(new RepeatRealTile());
        tiles.add(new RepeatIntTile());
        tiles.add(new LogTile());
        tiles.add(new ExpTile());
        tiles.add(new SqrtTile());
        tiles.add(new EnvTile());
        tiles.add(new RangeTile());
        tiles.add(new NumVectorTile());
        tiles.add(new NumRowsTile());
        tiles.add(new NumColsTile());
        tiles.add(new NumListTile());
        tiles.add(new NumSitesTile());
        tiles.add(new NumTaxaAlignmentTile());
        tiles.add(new NumTaxaTreeTile());
        tiles.add(new NumBranchesTile());
        tiles.add(new RootAgeTile());
        tiles.add(new LinSpaceTile());
        tiles.add(new TaxonTile());
        tiles.add(new AgeTaxonTile());
        tiles.add(new AgeNodeTreeTile());
        tiles.add(new MRCATile());
        tiles.add(new AlignmentTaxaTile());
        tiles.add(new TreeTaxaTile());
        tiles.add(new SumRealVectorTile());

        // BEAST X MCMC configuration
        tiles.add(new ChainLengthTile());
        tiles.add(new RandomSeedTile());
        tiles.add(new DefaultLogEveryTile());
        tiles.add(new OutputPrefixTile());
        tiles.add(new AutoOperatorConfigTile());
        tiles.add(new ScreenLoggerTile());
        tiles.add(new FileLoggerTile());
        tiles.add(new TreeLoggerTile());

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
        tiles.add(new BinomialTile());
        tiles.add(new BernoulliTile());
        tiles.add(new CategoricalTile());
        tiles.add(new DiscreteUniformTile());
        tiles.add(new GeometricTile());
        tiles.add(new DirichletTile());
        tiles.add(new MultivariateNormalTile());
        tiles.add(new IIDTile.RealIID());
        tiles.add(new IIDTile.PositiveRealIID());
        tiles.add(new TruncatedTile());
        tiles.add(new OffsetTile());

        // BEAST X tree priors
        tiles.add(new YuleTile());
        tiles.add(new BirthDeathTile());
        tiles.add(new FossilizedBirthDeathTile());
        tiles.add(new CoalescentTile());
        tiles.add(new SkylineCoalescentTile());
        tiles.add(new ConstantPopulationFunctionTile());
        tiles.add(new LogisticPopulationFunctionTile());
        tiles.add(new ExponentialPopulationFunctionTile());
        tiles.add(new CompoundPopulationFunctionTile());
        tiles.add(new CoalescentPopulationFunctionTile());

        // BEAST X branch models
        tiles.add(new DrawnBranchRatesTile());
        tiles.add(new StrictClockTile());
        tiles.add(new RelaxedClockTile());

        // BEAST X sequence likelihoods
        tiles.add(new VectorBranchRatesErrorTile());
        tiles.add(new VectorSiteRatesErrorTile());
        tiles.add(new PhyloCTMCTile());
        tiles.add(new PhyloBMTile());
        tiles.add(new PhyloOUTile());

        // BEAST X substitution models
        tiles.add(new JC69Tile());
        tiles.add(new K80Tile());
        tiles.add(new F81Tile());
        tiles.add(new HKYTile());
        tiles.add(new GTRTile());
        tiles.add(new GTRRelativeRatesTile());
        tiles.add(new JTTTile());
        tiles.add(new WAGTile());
        tiles.add(new LGTile());
        tiles.add(new GY94Tile());
        tiles.add(new MkTile());

        // BEAST X site models
        tiles.add(new DrawnSiteRatesTile());
        tiles.add(new SiteModelTile());

        return tiles;
    }
}
