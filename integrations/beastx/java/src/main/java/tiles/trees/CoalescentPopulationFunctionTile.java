package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.CoalescentSimulator;
import dr.evomodel.coalescent.TreeIntervals;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.coalescent.TreeIntervals;
import dr.evolution.util.Taxa;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.CoalescentSimulator;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.ExponentialGrowthModel;
import dr.evomodel.coalescent.demographicmodel.PiecewisePopulationModel;
import dr.evomodel.tree.DefaultTreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.model.StartingTreeSpec;

import java.util.IdentityHashMap;

public class CoalescentPopulationFunctionTile extends GeneratorTile<
        BoundDistribution<DefaultTreeModel, CoalescentLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Coalescent";
    }

    GeneratorTileInput<DemographicModel, BeastXState> populationSizeInput =
            new GeneratorTileInput<>("populationSize");

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BoundDistribution<DefaultTreeModel, CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        DemographicModel populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        StartingTreeSpec startingTreeSpec =
                startingTreeSpec(populationSize);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        startingTree(startingTreeSpec, taxa, populationSize)
                );

        TreeIntervals intervals =
                new TreeIntervals(defaultTreeModel);

        CoalescentLikelihood likelihood =
                new CoalescentLikelihood(
                        intervals,
                        populationSize
                );

        return new BoundDistribution<>(
                likelihood,
                defaultTreeModel,
                startingTreeSpec,
                treeModel -> {
                    // CoalescentLikelihood receives TreeIntervals built from the tree.
                }
        );
    }

    private static StartingTreeSpec startingTreeSpec(DemographicModel demographicModel) {
        if (demographicModel instanceof ConstantPopulationModel
                || demographicModel instanceof ExponentialGrowthModel
                || demographicModel instanceof PiecewisePopulationModel) {
            return StartingTreeSpec.coalescentSimulator();
        }

        return StartingTreeSpec.fixedNewick();
    }

    private static dr.evolution.tree.Tree startingTree(
            StartingTreeSpec startingTreeSpec,
            Taxa taxa,
            DemographicModel demographicModel
    ) {
        if (startingTreeSpec.type() == StartingTreeSpec.Type.COALESCENT_SIMULATOR) {
            CoalescentSimulator simulator =
                    new CoalescentSimulator();

            return simulator.simulateTree(taxa, demographicModel);
        }

        return InitialTreeBuilder.balancedTree(taxa, "Coalescent");
    }
}
