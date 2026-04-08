package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class StrictClockTile extends GeneratorTile<StrictClockModel> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "StrictClock";
    }

    TileInput<RealScalar<PositiveReal>> rateInput = new TileInput<>("rate");
    TileInput<Tree> treeInput = new TileInput<>("tree");

    @Override
    public StrictClockModel applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }

}
