package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.UCRelaxedClockModel;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.BoundDistribution;
import tiling.TypeToken;
import tiling.UnboundDistribution;

public class RelaxedClockTile extends GeneratorTile<UCRelaxedClockModel> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "RelaxedClock";
    }

    GeneratorTileInput<RealScalarParam<PositiveReal>> clockRateInput = new GeneratorTileInput<>("clockRate");
    GeneratorTileInput<BoundDistribution<? extends RealScalarParam<? extends PositiveReal>, ? extends ScalarDistribution<? extends RealScalar<? extends PositiveReal>, Double>>> baseInput = new GeneratorTileInput<>(
            "base"
    );
    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    protected UCRelaxedClockModel applyTile(BEASTState beastState) {
        UnboundDistribution<? extends RealScalarParam<? extends PositiveReal>, ? extends ScalarDistribution<? extends RealScalar<? extends PositiveReal>, Double>> base = this.baseInput.apply(beastState);
        RealScalarParam<PositiveReal> clockRate = this.clockRateInput.apply(beastState);
        Tree tree = this.treeInput.apply(beastState);

        // init the branch rate categories

        int numBranches = 2 * tree.getTaxaNames().length - 2;
        int[] rateArray = new int[numBranches];
        IntVectorParam<NonNegativeInt> rateCategories = new IntVectorParam<>(rateArray, NonNegativeInt.INSTANCE);
        beastState.addStateNode(rateCategories, new TypeToken<IntVectorParam<NonNegativeInt>>() {
        }, "branchRateCategories");

        // init the relaxed clock

        UCRelaxedClockModel relaxedClockModel = new UCRelaxedClockModel();
        // TODO: use type safe version of this
        relaxedClockModel.rateDistInput.setValue(base.distribution, relaxedClockModel);
        beastState.setInput(relaxedClockModel, relaxedClockModel.meanRateInput, clockRate);
        beastState.setInput(relaxedClockModel, relaxedClockModel.treeInput, tree);
        beastState.setInput(relaxedClockModel, relaxedClockModel.categoryInput, rateCategories);

        return relaxedClockModel;
    }

}
