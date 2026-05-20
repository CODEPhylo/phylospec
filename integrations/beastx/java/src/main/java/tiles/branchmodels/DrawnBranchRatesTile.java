package tiles.branchmodels;

import dr.evomodel.branchratemodel.BranchRateModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.TemplateTile;
import tiling.BeastXState;

import java.util.IdentityHashMap;

public class DrawnBranchRatesTile extends TemplateTile<BranchRateModel, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any branchRates ~ $branchRateDistribution";
    }

    TemplateTileInput<? extends BranchRateModel, BeastXState> branchRateDistributionInput =
            new TemplateTileInput<>("$branchRateDistribution");

    @Override
    protected BranchRateModel applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        return this.branchRateDistributionInput.apply(beastState, indexVariables);
    }
}