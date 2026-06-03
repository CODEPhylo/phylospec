package tiles.observations;

import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.DistributionLikelihood;
import dr.inference.distribution.UniformDistributionModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.TemplateTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.IdentityHashMap;
import java.util.List;

public class RootObservedBetweenTile extends TemplateTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    protected String getPhyloSpecTemplate() {
        return "Any x = rootAge(tree=$tree) observed between [$from, $to]";
    }

    TemplateTileInput<TreeModel, BeastXState> treeInput =
            new TemplateTileInput<>("$tree");

    TemplateTileInput<RealScalar<?>, BeastXState> fromInput =
            new TemplateTileInput<>("$from");

    TemplateTileInput<RealScalar<?>, BeastXState> toInput =
            new TemplateTileInput<>("$to");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        RealScalar<?> from =
                this.fromInput.apply(beastState, indexVariables);

        RealScalar<?> to =
                this.toInput.apply(beastState, indexVariables);

        if (from.get() < 0.0 || to.get() < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Root age calibration bounds must be non-negative.",
                    "Use calibration bounds greater than or equal to zero.",
                    List.of("Age root = rootAge(tree=tree) observed between [1.0, 5.0]")
            );
        }

        if (from.get() > to.get()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Root age calibration lower bound must not be greater than upper bound.",
                    "Use lower <= upper.",
                    List.of("Age root = rootAge(tree=tree) observed between [1.0, 5.0]")
            );
        }

        UniformDistributionModel distributionModel =
                new UniformDistributionModel(
                        new Parameter.Default(from.get()),
                        new Parameter.Default(to.get())
                );

        DistributionLikelihood calibrationPrior =
                new DistributionLikelihood(distributionModel);

        TMRCAStatistic rootAgeStatistic;

        try {
            rootAgeStatistic =
                    new TMRCAStatistic(
                            "rootAge",
                            tree,
                            null,
                            false,
                            false
                    );
        } catch (Exception exception) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Could not create a root age statistic for the tree.",
                    "Use a valid BEAST X tree model for rootAge(tree=tree).",
                    List.of("Age root = rootAge(tree=tree) observed between [1.0, 5.0]")
            );
        }

        calibrationPrior.addData(rootAgeStatistic);

        beastState.addCalibrationPriorDistribution(
                calibrationPrior,
                "rootCalibration"
        );

        return new BeastXRealScalarParam<>(
                rootAgeStatistic.getStatisticValue(0),
                NonNegativeReal.INSTANCE
        );
    }
}