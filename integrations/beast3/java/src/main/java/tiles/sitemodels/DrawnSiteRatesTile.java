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
        return "Any branchRates ~ $branchRateDistribution";
    }

    TemplateTileInput<? extends Partial<SiteModel, SubstitutionModel>> branchRateDistributionInput = new TemplateTileInput<>(
            "$branchRateDistribution"
    );

    @Override
    protected Partial<SiteModel, SubstitutionModel> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.branchRateDistributionInput.apply(beastState, indexVariables);
    }

}
