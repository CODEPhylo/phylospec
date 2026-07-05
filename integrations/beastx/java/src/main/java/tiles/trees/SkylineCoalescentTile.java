package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.TreeIntervals;
import dr.evomodel.coalescent.demographicmodel.PiecewisePopulationModel;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealVector;
import tiling.params.BeastXRealVectorParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.validation.BeastXValidation;

import java.util.IdentityHashMap;
import java.util.List;

public class SkylineCoalescentTile extends GeneratorTile<
        BoundDistribution<DefaultTreeModel, CoalescentLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "SkylineCoalescent";
    }

    GeneratorTileInput<RealVector<? extends PositiveReal>, BeastXState> populationSizesInput =
            new GeneratorTileInput<>("populationSizes");

    GeneratorTileInput<RealVector<? extends PositiveReal>, BeastXState> changeTimesInput =
            new GeneratorTileInput<>("changeTimes");

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BoundDistribution<DefaultTreeModel, CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealVector<? extends PositiveReal> populationSizes =
                this.populationSizesInput.apply(beastState, indexVariables);

        RealVector<? extends PositiveReal> changeTimes =
                this.changeTimesInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        if (populationSizes.size() < 2) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "SkylineCoalescent requires at least two population sizes.",
                    "Provide one population size for each skyline epoch.",
                    List.of("SkylineCoalescent(populationSizes=[100.0, 200.0], changeTimes=[1.0], taxa=taxa)")
            );
        }

        if (changeTimes.size() != populationSizes.size() - 1) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "SkylineCoalescent requires one fewer changeTimes value than populationSizes.",
                    "For n population sizes, provide n - 1 change times.",
                    List.of("SkylineCoalescent(populationSizes=[100.0, 200.0, 300.0], changeTimes=[1.0, 3.0], taxa=taxa)")
            );
        }

        double[] changeTimeValues =
                toArray(changeTimes);

        BeastXValidation.requireStrictlyIncreasing(
                changeTimeValues,
                this.getRootNode(),
                "SkylineCoalescent changeTimes must be strictly increasing.",
                "Provide ordered ages such as [1.0, 3.0, 5.0].",
                List.of("SkylineCoalescent(populationSizes=[100.0, 200.0, 300.0], changeTimes=[1.0, 3.0])")
        );

        double[] epochWidths =
                toEpochWidths(changeTimeValues);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "SkylineCoalescent")
                );

        PiecewisePopulationModel populationModel =
                new PiecewisePopulationModel(
                        "skylinePopulation",
                        toParameter(populationSizes),
                        epochWidths,
                        false,
                        Units.Type.YEARS
                );

        TreeIntervals intervals =
                new TreeIntervals(defaultTreeModel);

        CoalescentLikelihood likelihood =
                new CoalescentLikelihood(
                        intervals,
                        populationModel
                );

        return new BoundDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // CoalescentLikelihood receives TreeIntervals built from the initial tree.
                }
        );
    }

    private static Parameter toParameter(RealVector<? extends PositiveReal> vector) {
        if (vector instanceof BeastXRealVectorParam<?> beastXVector) {
            return beastXVector.getParameter();
        }

        return new Parameter.Default(toArray(vector));
    }

    private static double[] toArray(RealVector<? extends PositiveReal> vector) {
        double[] values =
                new double[(int) vector.size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = vector.get(i);
        }

        return values;
    }

    private static double[] toEpochWidths(double[] changeTimes) {
        double[] widths =
                new double[changeTimes.length];

        widths[0] = changeTimes[0];

        for (int i = 1; i < changeTimes.length; i++) {
            widths[i] = changeTimes[i] - changeTimes[i - 1];
        }

        return widths;
    }
}
