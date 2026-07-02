package tiles.trees;

import dr.evomodel.coalescent.TreeIntervals;
import dr.evolution.util.Taxa;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.CoalescentSimulator;
import dr.evomodel.coalescent.demographicmodel.DemographicModel;
import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.model.StartingTreeSpec;

import java.util.IdentityHashMap;

public class CoalescentPopulationFunctionTile extends GeneratorTile<
        BoundDistribution<TreeModel, CoalescentLikelihood>,
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
    public BoundDistribution<TreeModel, CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        DemographicModel populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        CoalescentSimulator simulator =
                new CoalescentSimulator();

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        simulator.simulateTree(taxa, populationSize)
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
                StartingTreeSpec.coalescentSimulator(),
                treeModel -> {
                    // CoalescentLikelihood receives TreeIntervals built from the tree.
                }
        );
    }
}
