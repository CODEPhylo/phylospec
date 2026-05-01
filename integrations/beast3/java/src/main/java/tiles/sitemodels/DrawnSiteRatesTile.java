package tiles.sitemodels;

import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;
import tiling.Partial;

import java.util.IdentityHashMap;

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
