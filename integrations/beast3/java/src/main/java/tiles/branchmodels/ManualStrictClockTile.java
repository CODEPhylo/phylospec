package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import tiles.TemplateTile;
import tiling.BEASTState;

public class ManualStrictClockTile extends TemplateTile<StrictClockModel> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any branchRates[i] = $rate for i in 1:numBranches(tree=$tree)";
    }

    TemplateTileInput<RealScalar<PositiveReal>> rateInput = new TemplateTileInput<>("$rate");
    TemplateTileInput<Tree> treeInput = new TemplateTileInput<>("$tree");

    @Override
    public StrictClockModel applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState);
        this.treeInput.apply(beastState);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }

}
