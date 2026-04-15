package tiles;

import tiles.branchmodels.*;
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

import java.util.ArrayList;
import java.util.List;

public class TileLibrary {
    private final static List<Tile<?>> tiles = new ArrayList<>();

    static {
        addTile(new AssignmentTile());
        addTile(new StateNodeAssignmentTile());
        addTile(new DrawTile());
        addTile(new LiteralTile<>());
        addTile(new VectorTile<>());
        addTile(new ListVectorTile<>());
        addTile(new DrawnArgumentTile());
        addTile(new AssignedArgumentTile());

        addTile(new ObservedAsTile());
        addTile(new ObservedAsAlignmentTile());
        addTile(new RootObservedBetweenTile());

        addTile(new EnvTile());
        addTile(new LogTile());
        addTile(new ExpTile());
        addTile(new SqrtTile());
        addTile(new LinSpaceTile());
        addTile(new RangeTile());
        addTile(new RepeatRealTile());
        addTile(new RepeatIntTile());
        addTile(new RepeatSimplexTile());

        addTile(new AlignmentTaxaTile());
        addTile(new TreeTaxaTile());
        addTile(new NumBranchesTile());
        addTile(new NumTaxaAlignmentTile());
        addTile(new NumTaxaTreeTile());
        addTile(new NumSitesTile());
        addTile(new NumTile());
        addTile(new NumRowsTile());
        addTile(new NumColsTile());

        addTile(new FromNexusTile());
        addTile(new FromTreeTile());
        addTile(new FromNewickTile());
        addTile(new ParserTile.Regex());
        addTile(new ParserTile.Delimiter());

        addTile(new SubsetTile());

        addTile(new OffsetTile());
        addTile(new NormalTile());
        addTile(new LogNormalTile());
        addTile(new LogNormalRealSpaceTile());
        addTile(new BetaTile());
        addTile(new CauchyTile());
        addTile(new DiscreteUniformTile());
        addTile(new ExponentialTile());
        addTile(new GammaTile());
        addTile(new PoissonTile());
        addTile(new UniformTile());
        addTile(new DirichletTile());

        addTile(new YuleTile());
        addTile(new BirthDeathTile());
        addTile(new ConstantCoalescentTile());
        addTile(new CoalescentTile());
        addTile(new ConstantPopulationTile());
        addTile(new ExponentialPopulationTile());

        addTile(new StrictClockTile());
        addTile(new ManualStrictClockTile());
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
        addTile(new PhyloCTMCAssignedBranchRatesTile());

        addTile(new ChainLengthTile());
        addTile(new ScreenLoggerTile());
        addTile(new FileLoggerTile());
        addTile(new TreeLoggerTile());
    }

    public static void addTile(Tile<?> tile) {
        tiles.add(tile);
    }

    public static List<Tile<?>> getTiles() {
        return tiles;
    }
}
