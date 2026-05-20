package tiles.trees;

import dr.evolution.coalescent.TreeIntervals;
import dr.evolution.tree.SimpleNode;
import dr.evolution.tree.SimpleTree;
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
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.BeastXTreeDistribution;

import java.util.IdentityHashMap;

public class CoalescentTile extends GeneratorTile<
        BeastXTreeDistribution<CoalescentLikelihood>,
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
    public BeastXTreeDistribution<CoalescentLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<? extends PositiveReal> populationSize =
                this.populationSizeInput.apply(beastState, indexVariables);

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel("tree", createInitialTree(taxa));

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

        return new BeastXTreeDistribution<>(
                likelihood,
                defaultTreeModel,
                treeModel -> {
                    // CoalescentLikelihood receives TreeIntervals built from the tree.
                    // This hook keeps the same binding shape as other tree distributions.
                }
        );
    }

    private static Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }

    private static SimpleTree createInitialTree(Taxa taxa) {
        if (taxa.getTaxonCount() < 2) {
            throw new IllegalArgumentException("Coalescent requires at least two taxa.");
        }

        SimpleNode root =
                buildBalancedSubtree(taxa, 0, taxa.getTaxonCount());

        root.setHeight(Math.max(root.getHeight(), 1.0));

        return new SimpleTree(root);
    }

    private static SimpleNode buildBalancedSubtree(
            Taxa taxa,
            int from,
            int to
    ) {
        if (to - from == 1) {
            SimpleNode leaf = new SimpleNode();
            leaf.setTaxon(taxa.getTaxon(from));
            leaf.setHeight(0.0);
            return leaf;
        }

        int mid =
                from + (to - from) / 2;

        SimpleNode left =
                buildBalancedSubtree(taxa, from, mid);

        SimpleNode right =
                buildBalancedSubtree(taxa, mid, to);

        SimpleNode parent = new SimpleNode();
        parent.addChild(left);
        parent.addChild(right);
        parent.setHeight(Math.max(left.getHeight(), right.getHeight()) + 1.0);

        return parent;
    }
}
