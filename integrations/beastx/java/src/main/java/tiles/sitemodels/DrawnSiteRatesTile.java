package tiles.sitemodels;

import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.evomodel.substmodel.SubstitutionModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;

/**
 * This tile handles site-rate draws such as:
 *
 * Vector<Rate> siteRates ~ DiscreteGammaInv(...)
 *
 * In BEAST X, this does not create an explicit vector-valued state node.
 * Instead, DiscreteGammaInv creates a partial site-rate model that will later
 * be completed with a substitution model by a PhyloCTMC/tree-likelihood tile.
 */
public class DrawnSiteRatesTile extends TemplateTile<
        Partial<GammaSiteRateModel, SubstitutionModel>,
        BeastXState
        > {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any siteRates ~ $siteRateDistribution";
    }

    TemplateTileInput<? extends Partial<GammaSiteRateModel, SubstitutionModel>, BeastXState>
            siteRateDistributionInput =
            new TemplateTileInput<>("$siteRateDistribution");

    @Override
    protected Partial<GammaSiteRateModel, SubstitutionModel> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        return this.siteRateDistributionInput.apply(beastState, indexVariables);
    }
}
