package tiles.trees;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeIntervals;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.BayesianSkyline;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.IntSimplexParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntSimplex;
import beast.base.spec.type.IntVector;
import beast.base.spec.type.RealVector;
import beastconfig.BEASTState;
import beastconfig.OperatorSelector;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import tiles.input.TaxaData;
import tiles.input.TaxonValue;
import tiling.BoundDistribution;

/**
 * {@code SkylineCoalescent(populationSizes, groupSizes, taxa)}: the classic Bayesian skyline,
 * where group i spans groupSizes[i] consecutive coalescent intervals with population size
 * populationSizes[i]. Maps to BEAST's {@link BayesianSkyline}.
 *
 * <p>The overload with {@code changeTimes} (fixed epoch boundaries) is a different model and is not
 * handled here.
 */
public class BayesianSkylineTile extends GeneratorTile<BoundDistribution<Tree, BayesianSkyline>, BEASTState> {

    GeneratorTileInput<RealVector<? extends PositiveReal>, BEASTState> populationSizesInput =
            new GeneratorTileInput<>("populationSizes");
    GeneratorTileInput<IntVector<? extends PositiveInt>, BEASTState> groupSizesInput =
            new GeneratorTileInput<>("groupSizes");
    GeneratorTileInput<List<TaxonValue>, BEASTState> taxaInput = new GeneratorTileInput<>("taxa", true);

    @Override
    public String getPhyloSpecGeneratorName() {
        return "SkylineCoalescent";
    }

    @Override
    public Optional<String> getNamespace() {
        return Optional.of("phylospec.distributions.tree");
    }

    @Override
    public BoundDistribution<Tree, BayesianSkyline> applyTile(
            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
        RealVector<? extends PositiveReal> populationSizes = this.populationSizesInput.apply(state, indices);
        IntVector<? extends PositiveInt> groupSizesVector = this.groupSizesInput.apply(state, indices);
        TaxaData taxa = TaxaData.fromList(state, this.taxaInput.apply(state, indices));

        int numTaxa = taxa.size();
        int events = numTaxa - 1;

        if (populationSizes.size() != groupSizesVector.size()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "SkylineCoalescent needs one population size per group, but got " + populationSizes.size()
                            + " population sizes and " + groupSizesVector.size() + " groups.",
                    "Make populationSizes and groupSizes the same length.");
        }

        IntSimplex<? extends PositiveInt> groupSizes;
        if (groupSizesVector instanceof IntSimplex<? extends PositiveInt> simplex) {
            groupSizes = simplex; // e.g. drawn from RandomComposition
        } else {
            // fixed group sizes: wrap them so BayesianSkyline gets the sum it expects
            int[] values = new int[groupSizesVector.size()];
            int sum = 0;
            for (int i = 0; i < values.length; i++) {
                values[i] = groupSizesVector.get(i);
                sum += values[i];
            }
            groupSizes = new IntSimplexParam<>(values, PositiveInt.INSTANCE, sum);
        }

        if (groupSizes.expectedSum() != events) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "The group sizes must sum to the number of coalescent events (" + events + " for " + numTaxa
                            + " taxa), but they sum to " + groupSizes.expectedSum() + ".",
                    "For random group sizes use RandomComposition(total=numTaxa(...) - 1, numParts=...).");
        }

        // default state: a random coalescent tree under a constant population
        ConstantPopulation populationFunction = new ConstantPopulation();
        state.setInput(
                populationFunction,
                populationFunction.popSizeParameter,
                new RealScalarParam<>(1.0, PositiveReal.INSTANCE));

        RandomTree defaultState = new RandomTree();
        state.setInput(defaultState, defaultState.m_taxonset, taxa.taxonSet());
        state.setInput(defaultState, defaultState.populationFunctionInput, populationFunction);
        if (taxa.ages() != null) {
            defaultState.setDateTrait(taxa.ages());
        }

        BayesianSkyline model = new BayesianSkyline();
        state.setInput(model, model.popSizeParamInput, populationSizes);
        state.setInput(model, model.groupSizeParamInput, groupSizes);

        return new BoundDistribution<>(
                model,
                defaultState,
                tree -> {
                    TreeIntervals intervals = new TreeIntervals();
                    state.setInput(intervals, intervals.treeInput, tree);
                    state.setInput(model, model.treeIntervalsInput, intervals);
                },
                OperatorSelector::getDefaultOperators);
    }
}
