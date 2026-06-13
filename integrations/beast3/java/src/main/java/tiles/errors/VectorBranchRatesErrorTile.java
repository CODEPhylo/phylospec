package tiles.errors;

import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.tree.Tree;
import beast.base.spec.evolution.likelihood.TreeLikelihood;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;
import tiling.TilePriority;
import tiling.TileApplicationError;
import tiling.UnboundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * This tile applies when a user specifies branch rates using a vector instead of on of the clock models.
 * This is currently not supported by BEAST.
 * Thus, this tile throws an error when it is applied successfully.
 */
public class VectorBranchRatesErrorTile extends TemplateTile<UnboundDistribution<Alignment, TreeLikelihood>> {

    @Override
    protected String getPhyloSpecTemplate() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<String> getPhyloSpecTemplates() {
        return List.of(
                // first template draws siteRates
                """
                        PhyloCTMC(
                           tree=$tree,
                           qMatrix=$substitutionModel,
                           branchRates=$branchRates,
                           siteRates~$$siteRates
                        )
                        """,
                // second template assigns siteRates. we don't care for this tile
                """
                        PhyloCTMC(
                           tree=$tree,
                           qMatrix=$substitutionModel,
                           branchRates=$branchRates,
                           siteRates=$$siteRates
                        )
                        """
        );
    }

    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree");
    TemplateTileInput<?> substitutionModelInput = new TemplateTileInput<>("$substitutionModel", true);
    TemplateTileInput<? extends RealVectorParam<?>> branchRatesInput = new TemplateTileInput<>("$branchRates", true);
    TemplateTileInput<?> siteRatesInput = new TemplateTileInput<>("$$siteRates", false);

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        throw new TileApplicationError(
                this.rootNode,
                "Explicit branch rates are not supported.",
                "Use either 'StrictClock' or 'RelaxedClock' to specify branch rates.",
                List.of("Vector<Rate> branchRates ~ StrictClock(rate=1.0)")
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.ERROR;
    }
}
