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
 * This tile applies when a user specifies site rates using a vector instead of 'DiscreteGammaInv'. This is currently
 * not supported by BEAST.
 * Thus, this tile throws an error when it is applied successfully.
 */
public class VectorSiteRatesErrorTile extends TemplateTile<UnboundDistribution<Alignment, TreeLikelihood>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return """
               PhyloCTMC(
                  tree=$tree,
                  qMatrix=$substitutionModel,
                  branchRates~$$branchRates,
                  siteRates=$siteRates
               )
               """;
    }

    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree");
    TemplateTileInput<?> substitutionModelInput = new TemplateTileInput<>("$substitutionModel", true);
    TemplateTileInput<?> branchRatesInput = new TemplateTileInput<>("$$branchRates", false);
    TemplateTileInput<? extends RealVectorParam<?>> siteRatesInput = new TemplateTileInput<>("$siteRates", true);

    @Override
    public UnboundDistribution<Alignment, TreeLikelihood> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        throw new TileApplicationError(
                this.rootNode,
                "Explicit site rates are not supported by BEAST 2.8.",
                "Use 'DiscreteGammaInv' to specify varying site rates, or don't specify any site rates for constant rates.",
                List.of("Vector<Rate> siteRates ~ DiscreteGammaInv(shape=1.0, numCategories=4, invariantProportion=0.1, numSites=numSites(data))")
        );
    }

    @Override
    public TilePriority getPriority() {
        return TilePriority.ERROR;
    }
}
