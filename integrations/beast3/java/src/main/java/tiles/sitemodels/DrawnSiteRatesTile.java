package tiles.sitemodels;

import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;
import tiling.Partial;

import java.util.IdentityHashMap;

/// This tile matches any draw where the drawn distribution is covered with a tile producing a BEAST substitution model.
/// The substitution model is then directly used as the output of this tile.
///
/// This accounts for the fact that in BEAST, we don't explicitly draw the site rates, but simply pass
/// the site model object to the `TreeLikelihood` object. Thus, the other tiles dealing with draws (e.g.
/// `DrawTile` or `DrawnArgumentTile`) don't apply here.
public class DrawnSiteRatesTile extends TemplateTile<Partial<SiteModel, SubstitutionModel>> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any siteRates ~ $siteRateDistribution";
    }

    TemplateTileInput<? extends Partial<SiteModel, SubstitutionModel>> siteRateDistributionInput = new TemplateTileInput<>(
            "$siteRateDistribution"
    );

    @Override
    protected Partial<SiteModel, SubstitutionModel> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.siteRateDistributionInput.apply(beastState, indexVariables);
    }

}
