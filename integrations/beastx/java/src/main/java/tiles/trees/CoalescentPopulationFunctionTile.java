package tiles.trees;

import dr.evolution.tree.SimpleTree;
import dr.evomodel.coalescent.TreeIntervals;
import dr.evolution.util.Taxa;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.CoalescentSimulator;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.coalescent.demographicmodel.LogisticGrowthModel;
import dr.evomodel.tree.DefaultTreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;
import tiling.model.StartingTreeSpec;
import tiling.model.TreeDistribution;

import java.util.IdentityHashMap;

public class CoalescentPopulationFunctionTile extends GeneratorTile<
        TreeDistribution<CoalescentLikelihood>,
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
    public TreeDistribution<CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        DemographicModel populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        StartingTree startingTree =
                startingTree(taxa, populationSize);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        startingTree.tree()
                );

        TreeIntervals intervals =
                new TreeIntervals(defaultTreeModel);

        CoalescentLikelihood likelihood =
                new CoalescentLikelihood(
                        intervals,
                        populationSize
                );

        return new TreeDistribution<>(
                likelihood,
                defaultTreeModel,
                startingTree.spec(),
                treeModel -> {
                    // CoalescentLikelihood receives TreeIntervals built from the tree.
                }
        );
    }

    private static StartingTree startingTree(
            Taxa taxa,
            DemographicModel populationSize
    ) {
        CoalescentSimulator simulator =
                new CoalescentSimulator();

        try {
            return new StartingTree(
                    simulator.simulateTree(taxa, populationSize),
                    StartingTreeSpec.coalescentSimulator()
            );
        } catch (RuntimeException exception) {
            if (populationSize instanceof LogisticGrowthModel && isMissingInverseIntensity(exception)) {
                return new StartingTree(
                        InitialTreeBuilder.balancedTree(taxa, "Coalescent"),
                        StartingTreeSpec.fixedNewick()
                );
            }

            throw exception;
        }
    }

    private static boolean isMissingInverseIntensity(RuntimeException exception) {
        Throwable current =
                exception;

        while (current != null) {
            String message =
                    current.getMessage();

            if (message != null && message.contains("Not implemented")) {
                return true;
            }

            current =
                    current.getCause();
        }

        return false;
    }

    private record StartingTree(
            SimpleTree tree,
            StartingTreeSpec spec
    ) {
    }
}
