package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evomodel.coalescent.BayesianSkylineLikelihood;
import dr.evomodel.tree.DefaultTreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.PositiveReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealVector;
import tiling.BeastXState;
import tiling.model.BoundDistribution;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * {@code SkylineCoalescent(populationSizes, groupSizes, taxa)}: the classic Bayesian skyline. Group i
 * spans groupSizes[i] consecutive coalescent intervals, counted from the tips, with population size
 * populationSizes[i]. Maps to BEAST X's stepwise {@link BayesianSkylineLikelihood}, as BEAST 3's
 * BayesianSkylineTile maps to BayesianSkyline.
 */
public class BayesianSkylineTile extends GeneratorTile<
        BoundDistribution<DefaultTreeModel, BayesianSkylineLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "SkylineCoalescent";
    }

    GeneratorTileInput<RealVector<? extends PositiveReal>, BeastXState> populationSizesInput =
            new GeneratorTileInput<>("populationSizes");

    // The group sizes must always sum to the number of coalescent events. Sampling them needs
    // RandomComposition, which BEAST X does not have yet, so only fixed group sizes are accepted.
    // BEAST X builds a fixed integer array such as [3, 2] as a list of integer scalars.
    GeneratorTileInput<List<? extends IntScalar<? extends PositiveInt>>, BeastXState> groupSizesInput =
            new GeneratorTileInput<>("groupSizes", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    GeneratorTileInput<Taxa, BeastXState> taxaInput =
            new GeneratorTileInput<>("taxa");

    @Override
    public BoundDistribution<DefaultTreeModel, BayesianSkylineLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealVector<? extends PositiveReal> populationSizes =
                this.populationSizesInput.apply(beastState, indexVariables);

        int[] groupSizes =
                this.groupSizesInput.apply(beastState, indexVariables).stream()
                        .mapToInt(IntScalar::get)
                        .toArray();

        Taxa taxa =
                this.taxaInput.apply(beastState, indexVariables);

        if (populationSizes.size() != groupSizes.length) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "SkylineCoalescent needs one population size per group, but got " + populationSizes.size()
                            + " population sizes and " + groupSizes.length + " groups.",
                    "Make populationSizes and groupSizes the same length.",
                    List.of("SkylineCoalescent(populationSizes=[1.0, 2.0], groupSizes=[3, 2], taxa=taxa)")
            );
        }

        // BEAST X does not reject every wrong sum: group sizes that are all 1 are silently replaced
        // by equal groups, which would change the model.
        int events = taxa.getTaxonCount() - 1;
        int sum = Arrays.stream(groupSizes).sum();

        if (sum != events) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "The group sizes must sum to the number of coalescent events (" + events + " for "
                            + taxa.getTaxonCount() + " taxa), but they sum to " + sum + ".",
                    "Give each group a number of coalescent events so that all " + events + " events are covered.",
                    List.of("SkylineCoalescent(populationSizes=[1.0, 2.0], groupSizes=[3, 2], taxa=taxa)")
            );
        }

        Parameter populationSizeParameter =
                SkylineCoalescentTile.toParameter(populationSizes);

        Parameter groupSizeParameter =
                new Parameter.Default(Arrays.stream(groupSizes).asDoubleStream().toArray());

        DefaultTreeModel defaultTreeModel =
                new DefaultTreeModel(
                        "tree",
                        InitialTreeBuilder.balancedTree(taxa, "SkylineCoalescent")
                );

        // BayesianSkylineLikelihood takes its tree in the constructor, so it is built once the
        // sampled or observed tree is known.
        return new BoundDistribution<>(
                defaultTreeModel,
                tree -> new BayesianSkylineLikelihood(
                        tree,
                        populationSizeParameter,
                        groupSizeParameter,
                        BayesianSkylineLikelihood.STEPWISE_TYPE
                )
        );
    }
}
