package mappings.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

/**
 * PhyloSpec mappings for BEAST 3 core population functions.
 *
 * <p>Each nested type is a compile-time declaration only; it is never instantiated. Argument
 * types are derived from the component library. Implementations are written as fully qualified
 * names because nested declarations may share their simple name with the mapped BEAST class.
 */
public final class CoalescentMappings {

    private CoalescentMappings() {}

    @GeneratorMapping(
            component = "phylospec.functions.coalescent.constantPopulationFunction",
            implementation = beast.base.spec.evolution.tree.coalescent.ConstantPopulation.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "populationSize", input = "popSizeParameter")
    public static final class ConstantPopulation {}

    @GeneratorMapping(
            component = "phylospec.functions.coalescent.exponentialPopulationFunction",
            implementation = beast.base.spec.evolution.tree.coalescent.ExponentialGrowth.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "populationSize", input = "popSizeParameterInput")
    @InputMapping(argument = "growthRate", input = "growthRateParameterInput")
    public static final class ExponentialPopulation {}
}
