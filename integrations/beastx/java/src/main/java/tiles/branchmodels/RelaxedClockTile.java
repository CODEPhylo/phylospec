package tiles.branchmodels;

import dr.evomodel.branchratemodel.DiscretizedBranchRates;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.ParametricDistributionModel;
import dr.math.distributions.Distribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.params.BeastXIntVectorParam;
import tiling.params.BeastXRealScalarParam;

import java.util.IdentityHashMap;
import java.util.List;

public class RelaxedClockTile extends GeneratorTile<DiscretizedBranchRates, BeastXState> {

    private static final double MEAN_TOLERANCE = 1.0e-6;

    @Override
    public String getPhyloSpecGeneratorName() {
        return "RelaxedClock";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> clockRateInput =
            new GeneratorTileInput<>("clockRate");

    GeneratorTileInput<
            BoundDistribution<
                    ? extends BeastXRealScalarParam<? extends PositiveReal>,
                    ? extends DistributionLikelihood
                    >,
            BeastXState
            > baseInput =
            new GeneratorTileInput<>("base");

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public DiscretizedBranchRates applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> clockRate =
                this.clockRateInput.apply(beastState, indexVariables);

        BoundDistribution<
                ? extends BeastXRealScalarParam<? extends PositiveReal>,
                ? extends DistributionLikelihood
                > base =
                this.baseInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        Distribution distribution =
                base.distribution.getDistribution();

        if (!(distribution instanceof ParametricDistributionModel parametricDistribution)) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "RelaxedClock requires a parametric base distribution.",
                    "Use a scalar distribution such as LogNormal, Gamma, or Exponential as the base distribution.",
                    List.of("RelaxedClock(clockRate=1.0, base=LogNormal(mean=1.0, logSd=0.1), tree=tree)")
            );
        }

        double baseMean =
                distribution.mean();

        if (Double.isNaN(baseMean) || Math.abs(baseMean - 1.0) > MEAN_TOLERANCE) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "RelaxedClock base distribution must have mean 1.0.",
                    "Use a base distribution with mean 1.0 so the mean branch rate is controlled by clockRate.",
                    List.of("RelaxedClock(clockRate=1.0, base=LogNormal(mean=1.0, logSd=0.1), tree=tree)")
            );
        }

        int numBranches =
                tree.getNodeCount() - 1;

        if (numBranches <= 0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "RelaxedClock requires a tree with at least one branch.",
                    "Use a tree with at least two taxa.",
                    List.of("Tree tree ~ Yule(birthRate=1.0, taxa=taxa)")
            );
        }

        BeastXIntVectorParam<NonNegativeInt> rateCategories =
                new BeastXIntVectorParam<>(
                        new int[numBranches],
                        NonNegativeInt.INSTANCE
                );

        String categoryId =
                this.getId("branchRateCategories", indexVariables, "");

        beastState.addStateNode(
                rateCategories,
                new TypeToken<BeastXIntVectorParam<NonNegativeInt>>() {},
                categoryId
        );

        DiscretizedBranchRates relaxedClock =
                new DiscretizedBranchRates(
                        tree,
                        rateCategories.getParameter(),
                        parametricDistribution,
                        1,
                        true,
                        clockRate.get(),
                        false,
                        false,
                        false
                );

        relaxedClock.setId(
                beastState.getAvailableID(
                        this.getId("relaxedClock", indexVariables, "")
                )
        );

        beastState.addTreeRelaxedClockModel(
                tree,
                relaxedClock,
                rateCategories.getParameter(),
                parametricDistribution,
                clockRate.get()
        );

        return relaxedClock;
    }
}