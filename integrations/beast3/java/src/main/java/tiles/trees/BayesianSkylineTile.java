package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.BayesianSkyline;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntSimplex;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.DecoratedAlignment;
import tiling.BoundDistribution;

/** Event-group skyline; the group parameter owns its prior and sum-preserving operators. */
public final class BayesianSkylineTile extends GeneratorTile<BoundDistribution<Tree, BayesianSkyline>, BEASTState> {
    GeneratorTileInput<RealVector<? extends PositiveReal>, BEASTState> populationSizesInput =
            new GeneratorTileInput<>("populationSizes");
    GeneratorTileInput<IntSimplex<? extends PositiveInt>, BEASTState> groupSizesInput =
            new GeneratorTileInput<>("groupSizes");
    GeneratorTileInput<DecoratedAlignment, BEASTState> taxaInput = new GeneratorTileInput<>("taxa");

    @Override
    public String getPhyloSpecGeneratorName() {
        return "BayesianSkyline";
    }

    @Override
    public BoundDistribution<Tree, BayesianSkyline> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        var populations = populationSizesInput.apply(state, indices);
        var groups = groupSizesInput.apply(state, indices);
        var taxa = taxaInput.apply(state, indices);
        int events = taxa.taxonSet().getTaxonCount() - 1;
        if (populations.size() != groups.size() || groups.size() < 1 || groups.size() > events) {
            throw invalid(
                    "Population sizes and group sizes must have the same positive dimension, at most taxa.num - 1.");
        }
        long sum = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i) < 1 || !Double.isFinite(populations.get(i)) || populations.get(i) <= 0.0) {
                throw invalid("Group sizes and initial population sizes must be positive and finite.");
            }
            sum += groups.get(i);
        }
        if (sum != events || groups.expectedSum() != events) {
            throw invalid("Group sizes must sum to taxa.num - 1 (" + events + ").");
        }

        var initialPopulation = new ConstantPopulation();
        state.setInput(
                initialPopulation,
                initialPopulation.popSizeParameter,
                new RealScalarParam<>(1.0, PositiveReal.INSTANCE));
        var initialTree = new RandomTree();
        state.setInput(initialTree, initialTree.taxaInput, taxa.alignment());
        state.setInput(initialTree, initialTree.m_taxonset, taxa.taxonSet());
        state.setInput(initialTree, initialTree.populationFunctionInput, initialPopulation);
        if (taxa.ages() != null) initialTree.setDateTrait(taxa.ages());

        var skyline = new BayesianSkyline();
        state.setInput(skyline, skyline.popSizeParamInput, populations);
        state.setInput(skyline, skyline.groupSizeParamInput, groups);
        return new BoundDistribution<>(
                skyline,
                initialTree,
                tree -> {
                    var intervals = new TreeIntervals();
                    state.setInput(intervals, intervals.treeInput, tree);
                    if (tree.getInternalNodeCount() != events) throw invalid("A rooted binary tree is required.");
                    state.setInput(skyline, skyline.treeIntervalsInput, intervals);
                },
                OperatorSelector::getDefaultOperators);
    }

    private static TileApplicationError invalid(String detail) {
        return new TileApplicationError(
                "Invalid BayesianSkyline configuration. " + detail,
                "Use one population size per group and UniformComposition(total=taxa.num - 1, num=numberOfGroups). "
                        + "Supply the fixed integer total explicitly; do not use changeTimes for this model.");
    }
}
