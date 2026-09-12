package mappings.popfunc;

import adapters.popfunc.IndicatorFallback;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_t50;

@GeneratorMapping(
        component = "popfunc.functions.coalescent.gompertzT50PopulationFunction",
        implementation = GompertzGrowth_t50.class,
        output = PopulationFunction.class)
public interface GompertzT50Mapping {

    @InputMapping(argument = "halfCapacityAge", input = "t50Input")
    RealScalar<? extends NonNegativeReal> halfCapacityAge();

    @InputMapping(argument = "growthRate", input = "bInput")
    RealScalar<? extends PositiveReal> growthRate();

    @InputMapping(argument = "carryingCapacity", input = "NInfinityInput")
    RealScalar<? extends PositiveReal> carryingCapacity();

    @InputMapping(argument = "ancestralPopulationSize", input = "NAInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "indicatorParameterInput",
            fallback = IndicatorFallback.class)
    RealScalar<? extends PositiveReal> ancestralPopulationSize();
}
