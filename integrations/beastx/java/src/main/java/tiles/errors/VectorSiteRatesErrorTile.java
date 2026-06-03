package tiles.errors;

import dr.evolution.alignment.Alignment;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.tiling.tiles.TilePriority;
import org.phylospec.types.RealVector;
import tiling.BeastXState;
import tiling.model.UnboundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

public class VectorSiteRatesErrorTile extends TemplateTile<UnboundDistribution<Alignment>, BeastXState> {

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
                    branchRates~$$branchRates,
                    siteRates=$siteRates
                )
                """,
                """
                PhyloCTMC(
                    tree=$tree,
                    qMatrix=$qMatrix,
                    branchRates=$$branchRates,
                    siteRates=$siteRates
                )
                """,
                """
                PhyloCTMC(
                    tree=$tree,
                    qMatrix=$qMatrix,
                    siteRates=$siteRates
                )
                """
        );
    }

    TemplateTileInput<TreeModel, BeastXState> treeInput =
            new TemplateTileInput<>("$tree");

    TemplateTileInput<SubstitutionModel, BeastXState> qMatrixInput =
            new TemplateTileInput<>("$qMatrix");

    TemplateTileInput<? extends BranchRateModel, BeastXState> branchRatesInput =
            new TemplateTileInput<>("$$branchRates", false);

    TemplateTileInput<RealVector<?>, BeastXState> siteRatesInput =
            new TemplateTileInput<>("$siteRates");

    @Override
    public UnboundDistribution<Alignment> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        throw new TileApplicationError(
                this.rootNode,
                "Explicit vector site rates are not supported by BEAST X PhyloCTMC.",
                "Use DiscreteGammaInv(...) for site-rate heterogeneity, or omit siteRates for constant site rates.",
                List.of("Vector<Rate> siteRates ~ DiscreteGammaInv(shape=1.0, numCategories=4, invariantProportion=0.1, numSites=numSites(data))")
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.ERROR;
    }
}