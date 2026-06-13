package tiles.branchmodels;

import beast.base.spec.evolution.branchratemodel.Base;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;

import java.util.IdentityHashMap;

/// This tile matches any draw where the drawn distribution is covered with a tile producing a BEAST branch model.
/// The branch model is then directly used as the output of this tile.
///
/// This accounts for the fact that in BEAST, we don't always explicitly draw the branch rates, but simply pass
/// the branch model object to the `TreeLikelihood` object. Thus, the other tiles dealing with draws (e.g.
/// `DrawTile` or `DrawnArgumentTile`) don't apply here.
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
