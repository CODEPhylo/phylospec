package tiles.errors;

import dr.evolution.alignment.Alignment;
import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.types.RealVector;
import tiling.BeastXState;
import tiling.UnboundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

public class VectorBranchRatesErrorTile extends TemplateTile<UnboundDistribution<Alignment>, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<String> getPhyloSpecTemplates() {
        return List.of(
                """
                PhyloCTMC(
                    tree=$tree,
                    qMatrix=$qMatrix,
                    branchRates=$branchRates,
                    siteRates~$$siteRates
                )
                """,
                """
                PhyloCTMC(
                    tree=$tree,
                    qMatrix=$qMatrix,
                    branchRates=$branchRates,
                    siteRates=$$siteRates
                )
                """,
                """
                PhyloCTMC(
                    tree=$tree,
                    qMatrix=$qMatrix,
                    branchRates=$branchRates
                )
                """
        );
    }

    TemplateTileInput<TreeModel, BeastXState> treeInput =
            new TemplateTileInput<>("$tree");

    TemplateTileInput<SubstitutionModel, BeastXState> qMatrixInput =
            new TemplateTileInput<>("$qMatrix");

    TemplateTileInput<RealVector<?>, BeastXState> branchRatesInput =
            new TemplateTileInput<>("$branchRates");

    TemplateTileInput<? extends Partial<GammaSiteRateModel, SubstitutionModel>, BeastXState> siteRatesInput =
            new TemplateTileInput<>("$$siteRates", false);

    @Override
    public UnboundDistribution<Alignment> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        throw new TileApplicationError(
                this.rootNode,
                "Explicit vector branch rates are not supported by BEAST X PhyloCTMC.",
                "Use StrictClock(...) or RelaxedClock(...) for branch-rate models.",
                List.of("Vector<Rate> branchRates ~ StrictClock(rate=1.0)")
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.ERROR;
    }
}