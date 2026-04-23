package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import tiles.TemplateTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class ManualStrictClockTile extends TemplateTile<StrictClockModel> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any branchRates[i] = $rate for i in 1:numBranches(tree=$tree)";
    }

    TemplateTileInput<RealScalar<PositiveReal>> rateInput = new TemplateTileInput<>("$rate");
    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree");

    @Override
    public StrictClockModel applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);
        this.treeInput.apply(beastState, indexVariables);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }

}
