package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import tiles.MultiAstNodeTile;
import tiling.BEASTState;

public class ManualStrictClockTile extends MultiAstNodeTile<StrictClockModel> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any branchRates[i] = $rate for i in 1:numBranches(tree=$tree)";
    }

    MultiAstNodeTileInput<RealScalar<PositiveReal>> rateInput = new MultiAstNodeTileInput<>("$rate");
    MultiAstNodeTileInput<Tree> treeInput = new MultiAstNodeTileInput<>("$tree");

    @Override
    public StrictClockModel applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState);
        this.treeInput.apply(beastState);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }

}
