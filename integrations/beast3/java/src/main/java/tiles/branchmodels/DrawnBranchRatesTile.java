package tiles.branchmodels;

import beast.base.spec.evolution.branchratemodel.Base;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;

import java.util.IdentityHashMap;

public class DrawnBranchRatesTile extends TemplateTile<Base> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any branchRates ~ $branchRateDistribution";
    }

    TemplateTileInput<? extends Base> branchRateDistributionInput = new TemplateTileInput<>(
            "$branchRateDistribution"
    );

    @Override
    protected Base applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.branchRateDistributionInput.apply(beastState, indexVariables);
    }

}
