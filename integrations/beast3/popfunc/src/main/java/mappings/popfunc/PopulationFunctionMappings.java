package mappings.popfunc;

import adapters.popfunc.IndicatorFallback;
import adapters.popfunc.PositiveProbabilityAdapter;
import adapters.popfunc.UnitPositiveFallback;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

/**
 * PhyloSpec mappings for PopFunc population functions.
 *
 * <p>Each nested type is a compile-time declaration only; it is never instantiated. Argument
 * types are derived from the component library. Only BEAST Inputs that do not follow the
 * {@code argument -> argumentInput} convention are declared explicitly.
 *
 * <p>Implementations are written as fully qualified names because several nested declarations
 * share their simple name with the PopFunc class they map.
 */
public final class PopulationFunctionMappings {

    private PopulationFunctionMappings() {}

    @GeneratorMapping(
            component = "popfunc.functions.coalescent.constantExponentialConstantPopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.Cons_Exp_ConsGrowth.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "recentPopulationSize", input = "NCInput")
    @InputMapping(argument = "growthRate", input = "rInput")
    @InputMapping(argument = "growthStartAge", input = "xInput")
    @InputMapping(argument = "growthEndAge", input = "tauInput")
    public static final class ConsExpCons {}

    @GeneratorMapping(
            component = "phylospec.functions.coalescent.constantPopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.ConstantGrowth.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "populationSize", input = "popSizeParameterInput")
    public static final class ConstantGrowth {}

    @GeneratorMapping(
            component = "phylospec.functions.coalescent.expansionPopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.ExpansionGrowth.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "populationSize", input = "NCInput")
    @InputMapping(argument = "growthRate", input = "rInput")
    @InputMapping(argument = "transitionAge", input = "xInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "NAInput",
            fallback = UnitPositiveFallback.class)
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "I_naInput",
            fallback = IndicatorFallback.class)
    public static final class ExpansionGrowth {}

    @GeneratorMapping(
            component = "phylospec.functions.coalescent.exponentialPopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.ExponentialGrowth.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "populationSize", input = "popSizeParameterInput")
    @InputMapping(argument = "growthRate", input = "growthRateParameterInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "ancestralPopulationParameterInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "indicatorParameterInput",
            fallback = IndicatorFallback.class)
    public static final class ExponentialGrowth {}

    @GeneratorMapping(
            component = "popfunc.functions.coalescent.gompertzF0PopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.GompertzGrowth_f0.class,
            output = PopulationFunction.class)
    @InputMapping(
            argument = "initialProportion",
            input = "f0Input",
            adapter = PositiveProbabilityAdapter.class)
    @InputMapping(argument = "growthRate", input = "bInput")
    @InputMapping(argument = "initialPopulationSize", input = "N0Input")
    @InputMapping(argument = "ancestralPopulationSize", input = "NAInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "indicatorParameterInput",
            fallback = IndicatorFallback.class)
    public static final class GompertzF0 {}

    @GeneratorMapping(
            component = "popfunc.functions.coalescent.gompertzT50PopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.GompertzGrowth_t50.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "halfCapacityAge", input = "t50Input")
    @InputMapping(argument = "growthRate", input = "bInput")
    @InputMapping(argument = "carryingCapacity", input = "NInfinityInput")
    @InputMapping(argument = "ancestralPopulationSize", input = "NAInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "indicatorParameterInput",
            fallback = IndicatorFallback.class)
    public static final class GompertzT50 {}

    @GeneratorMapping(
            component = "popfunc.functions.coalescent.logisticPopulationFunction",
            implementation = popfunc.beast.evolution.populationmodel.LogisticGrowth.class,
            output = PopulationFunction.class)
    @InputMapping(argument = "inflectionAge", input = "t50Input")
    @InputMapping(argument = "carryingCapacity", input = "nCarryingCapacityInput")
    @InputMapping(argument = "growthRate", input = "bInput")
    @InputMapping(argument = "ancestralPopulationSize", input = "NAInput")
    public static final class LogisticGrowth {}

    /** Both arguments follow the naming convention: indicator -> indicatorInput, models -> modelsInput. */
    @GeneratorMapping(
            component = "popfunc.functions.coalescent.stochasticPopulationSelection",
            implementation = popfunc.beast.evolution.populationmodel.StochasticVariableSelection.class,
            output = PopulationFunction.class)
    public static final class StochasticPopulationSelection {}
}
