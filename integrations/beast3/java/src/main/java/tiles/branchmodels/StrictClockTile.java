package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class StrictClockTile extends GeneratorTile<StrictClockModel> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "StrictClock";
    }

    GeneratorTileInput<RealScalar<PositiveReal>> rateInput = new GeneratorTileInput<>("rate");
    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public StrictClockModel applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState);
        this.treeInput.apply(beastState);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }

}
