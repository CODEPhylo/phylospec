package mappings.popfunc;

import adapters.popfunc.AncestralIndicatorAdapter;
import adapters.popfunc.IndicatorFallback;
import adapters.popfunc.UnitPositiveFallback;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.ExpansionGrowth;

@GeneratorMapping(
        component = "phylospec.functions.coalescent.expansionPopulationFunction",
        implementation = ExpansionGrowth.class,
        output = PopulationFunction.class)
public interface ExpansionGrowthMapping {

    @InputMapping(argument = "populationSize", input = "NCInput")
    RealScalar<? extends PositiveReal> populationSize();

    @InputMapping(argument = "growthRate", input = "rInput")
    RealScalar<? extends PositiveReal> growthRate();

    @InputMapping(argument = "transitionAge", input = "xInput")
    RealScalar<? extends NonNegativeReal> transitionAge();

    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "NAInput",
            fallback = UnitPositiveFallback.class)
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "I_naInput",
            adapter = AncestralIndicatorAdapter.class,
            fallback = IndicatorFallback.class)
    RealScalar<? extends PositiveReal> ancestralPopulationSize();
}
