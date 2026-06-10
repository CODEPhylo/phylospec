package tiles.trees;

import dr.evolution.coalescent.TreeIntervals;
import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.TreeDistribution;

import java.util.IdentityHashMap;

public class CoalescentTile extends GeneratorTile<
        TreeDistribution<CoalescentLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Coalescent";
    }

    GeneratorTileInput<RealScalar<? extends PositiveReal>, BeastXState> populationSizeInput =
            new GeneratorTileInput<>("populationSize");

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public TreeDistribution<CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "Coalescent")
                );

        ConstantPopulationModel populationModel =
                new ConstantPopulationModel(
                        "constantPopulation",
                        toParameter(populationSize),
                        Units.Type.YEARS
                );

        TreeIntervals intervals =
                new TreeIntervals(defaultTreeModel);

        CoalescentLikelihood likelihood =
                new CoalescentLikelihood(
                        intervals,
                        populationModel
                );

        return new TreeDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // CoalescentLikelihood receives TreeIntervals built from the tree.
                }
        );
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }
}