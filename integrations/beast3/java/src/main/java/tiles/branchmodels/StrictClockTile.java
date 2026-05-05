package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import beastconfig.BEASTState;

import java.util.IdentityHashMap;

public class StrictClockTile extends GeneratorTile<StrictClockModel, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "StrictClock";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> rateInput = new GeneratorTileInput<>("clockRate");
    GeneratorTileInput<Tree, BEASTState> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public StrictClockModel applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);
        this.treeInput.apply(beastState, indexVariables);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }

}
