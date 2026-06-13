package tiles.branchmodels;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.branchratemodel.UCRelaxedClockModel;
import beast.base.spec.inference.distribution.ScalarDistribution;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import org.phylospec.ast.Expr;
import tiles.GeneratorTile;
import tiling.*;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    protected UCRelaxedClockModel applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        UnboundDistribution<? extends RealScalarParam<? extends PositiveReal>, ? extends ScalarDistribution<? extends RealScalar<? extends PositiveReal>, Double>> base = this.baseInput.apply(beastState, indexVariables);
        RealScalarParam<PositiveReal> clockRate = this.clockRateInput.apply(beastState, indexVariables);
        Tree tree = this.treeInput.apply(beastState, indexVariables);

        // make sure that the distribution has mean rate 1.0

        beastState.initBEASTObject(base.distribution);
        if (1E-6 < Math.abs(base.distribution.getMean() - 1.0)) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Base distribution used for the relaxed clock should have a mean of 1.0. You use a distribution with mean " + base.distribution.getMean() + ".",
                    "Use a distribution with mean 1.0.",
                    List.of("LogNormal(mean=1.0, logSd=0.1)")
            );
        }

        // init the branch rate categories

        String id = this.getId("branchRateCategories", indexVariables, "");

        int numBranches = 2 * tree.getTaxaNames().length - 2;
        int[] rateArray = new int[numBranches];
        IntVectorParam<NonNegativeInt> rateCategories = new IntVectorParam<>(rateArray, NonNegativeInt.INSTANCE);
        beastState.addStateNode(rateCategories, new TypeToken<IntVectorParam<NonNegativeInt>>() {
        }, id);

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
