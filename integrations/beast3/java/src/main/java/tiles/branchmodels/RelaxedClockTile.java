package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.evolution.branchratemodel.UCRelaxedClockModel;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Tile;

public class RelaxedClockTile extends GeneratorTile<UCRelaxedClockModel> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "RelaxedClock";
    }

    Input<RealScalar<PositiveReal>> baseInput = new Input<>("base");
    Input<Tree> treeInput = new Input<>("tree", false);

    @Override
    public UCRelaxedClockModel applyTile(BEASTState beastState) {
        return null;
    }

    @Override
    protected Tile<?> createInstance() {
        return new RelaxedClockTile();
    }

}
