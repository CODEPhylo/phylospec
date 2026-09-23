package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Units;
import dr.evomodel.coalescent.CoalescentLikelihood;
import dr.evomodel.coalescent.CoalescentSimulator;
import dr.evomodel.coalescent.TreeIntervals;
import dr.evomodel.coalescent.demographicmodel.ConstantPopulationModel;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.model.StartingTreeSpec;
import tiling.operators.ParameterRole;

import java.util.IdentityHashMap;

public class CoalescentTile extends GeneratorTile<
        BoundDistribution<DefaultTreeModel, CoalescentLikelihood>,
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
    public BoundDistribution<DefaultTreeModel, CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        Parameter populationSizeParameter =
                toParameter(populationSize);

        beastState.addParameterRole(
                populationSizeParameter,
                ParameterRole.DEMOGRAPHIC_SCALE);

        ConstantPopulationModel populationModel =
                new ConstantPopulationModel(
                        "constantPopulation",
                        populationSizeParameter,
                        Units.Type.YEARS
                );

        CoalescentSimulator simulator =
                new CoalescentSimulator();

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        simulator.simulateTree(taxa, populationModel)
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
                StartingTreeSpec.coalescentSimulator(),
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
