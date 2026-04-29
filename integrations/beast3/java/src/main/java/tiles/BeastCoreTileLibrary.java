package tiles;

import tiles.misc.RangeTile;
import tiles.rpn.BinaryTile;
import tiles.rpn.ExpRPNTile;
import tiles.rpn.LogRPNTile;
import tiles.rpn.RPNAssignmentTile;
import tiles.branchmodels.*;
import tiles.errors.*;
import tiles.functions.*;
import tiles.input.*;
import tiles.mcmc.*;
import tiles.observations.*;
import tiles.sitemodels.*;
import tiles.trees.*;
import tiling.Tile;
import tiles.distributions.*;
import tiles.misc.*;
import tiles.substitutionmodels.*;
import tiling.TileFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This class loads all known tiles into a static field.
 */
public class BeastCoreTileLibrary extends TileLibrary {

    @Override
    public List<TileFactory> getTiles() {
        List<TileFactory> tiles = new ArrayList<>();

        tiles.add(new AssignmentTile());
        tiles.add(new DrawTile());
        tiles.add(new LiteralTile<>());
        tiles.add(new VectorTile<>());
        tiles.add(new ListVectorTile());
        tiles.add(new DrawnArgumentTile());
        tiles.add(new AssignedArgumentTile());

        tiles.add(new RangeTile());
        tiles.add(new IndexVariableTile());
        tiles.add(new IndexedTile());
        tiles.add(new IndexedStatementTile());

        tiles.add(new ObservedAsTile());
        tiles.add(new ObservedAsAlignmentTile());
        tiles.add(new RootObservedBetweenTile());

        tiles.add(new BinaryTile.RpnRpn());
        tiles.add(new BinaryTile.RpnReal());
        tiles.add(new BinaryTile.RealRpn());
        tiles.add(new BinaryTile.RealReal());
        tiles.add(new LogRPNTile.Real());
        tiles.add(new LogRPNTile.Rpn());
        tiles.add(new ExpRPNTile.Real());
        tiles.add(new ExpRPNTile.Rpn());
        tiles.add(new RPNAssignmentTile());

        tiles.add(new EnvTile());
        tiles.add(new LogTile());
        tiles.add(new ExpTile());
        tiles.add(new SqrtTile());
        tiles.add(new LinSpaceTile());
        tiles.add(new tiles.functions.RangeTile());
        tiles.add(new RepeatRealTile());
        tiles.add(new RepeatIntTile());
        tiles.add(new RepeatSimplexTile());

        tiles.add(new AlignmentTaxaTile());
        tiles.add(new NumBranchesTile());
        tiles.add(new NumTaxaAlignmentTile());
        tiles.add(new NumTaxaTreeTile());
        tiles.add(new NumSitesTile());
        tiles.add(new NumVectorTile());
        tiles.add(new NumListTile());
        tiles.add(new NumRowsTile());
        tiles.add(new NumColsTile());

        tiles.add(new FromNexusTile());
        tiles.add(new FromTreeTile());
        tiles.add(new FromNewickTile());
        tiles.add(new ParserTile.Regex());
        tiles.add(new ParserTile.Delimiter());

        tiles.add(new SubsetTile());

        tiles.add(new OffsetTile());
        tiles.add(new NormalTile());
        tiles.add(new LogNormalTile());
        tiles.add(new LogNormalRealSpaceTile());
        tiles.add(new BetaTile());
        tiles.add(new CauchyTile());
        tiles.add(new DiscreteUniformTile());
        tiles.add(new ExponentialTile());
        tiles.add(new GammaTile());
        tiles.add(new PoissonTile());
        tiles.add(new UniformTile());
        tiles.add(new DirichletTile());

        tiles.add(new YuleTile());
        tiles.add(new BirthDeathTile());
        tiles.add(new ConstantCoalescentTile());
        tiles.add(new CoalescentTile());
        tiles.add(new ConstantPopulationTile());
        tiles.add(new ExponentialPopulationTile());

        tiles.add(new StrictClockTile());
        tiles.add(new ManualStrictClockTile());
        tiles.add(new RelaxedClockTile());
        tiles.add(new DrawnBranchRatesTile());
        tiles.add(new DrawnSiteRatesTile());

        tiles.add(new JC69Tile());
        tiles.add(new K80Tile());
        tiles.add(new F81Tile());
        tiles.add(new HKYTile());
        tiles.add(new GTRTile());
        tiles.add(new WAGTile());
        tiles.add(new JTTTile());

        tiles.add(new SiteModelTile());
        tiles.add(new PhyloCTMCTile());
        tiles.add(new VectorBranchRatesErrorTile());
        tiles.add(new VectorSiteRatesErrorTile());

        tiles.add(new ChainLengthTile());
        tiles.add(new ScreenLoggerTile());
        tiles.add(new FileLoggerTile());
        tiles.add(new TreeLoggerTile());

        return tiles;
    }
}
