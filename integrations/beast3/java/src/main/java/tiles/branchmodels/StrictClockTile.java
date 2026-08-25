package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.StrictClockModel;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import org.phylospec.annotations.PhyloParam;
import org.phylospec.annotations.PhyloSpec;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.AnnotatedGeneratorTile;

@PhyloSpec(value = "StrictClock", role = PhyloSpec.Role.CLOCK_MODEL)
public class StrictClockTile extends AnnotatedGeneratorTile<StrictClockModel, BEASTState> {

    @PhyloParam("clockRate")
    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> rateInput = input();

    @PhyloParam("tree")
    GeneratorTileInput<Tree, BEASTState> treeInput = input();

    @Override
    public StrictClockModel applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalar<PositiveReal> rate = this.rateInput.apply(beastState, indexVariables);
        this.treeInput.apply(beastState, indexVariables);

        StrictClockModel strictClockModel = new StrictClockModel();
        beastState.setInput(strictClockModel, strictClockModel.meanRateInput, rate);

        return strictClockModel;
    }
}
