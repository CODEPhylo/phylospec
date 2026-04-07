package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.substitutionmodel.GTR;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.RealVector;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class StrictClockTile extends GeneratorTile<StrictClockModel> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "StrictClock";
    }

    Input<RealScalar<PositiveReal>> rateInput = new Input<>("rate");
    Input<Tree> treeInput = new Input<>("tree", false);

    @Override
    public StrictClockModel applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState);

        StrictClockModel strictClockModel = new StrictClockModel();
        strictClockModel.setInputValue("clock.rate", rate);

        return strictClockModel;
    }

    @Override
    protected Tile<?> createInstance() {
        return new StrictClockTile();
    }

}
