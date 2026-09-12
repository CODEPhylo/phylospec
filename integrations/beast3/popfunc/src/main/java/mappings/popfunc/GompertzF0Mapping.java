package mappings.popfunc;

import adapters.popfunc.IndicatorFallback;
import adapters.popfunc.PositiveProbabilityAdapter;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.GompertzGrowth_f0;

@GeneratorMapping(
        component = "popfunc.functions.coalescent.gompertzF0PopulationFunction",
        implementation = GompertzGrowth_f0.class,
        output = PopulationFunction.class)
public interface GompertzF0Mapping {

    @InputMapping(
            argument = "initialProportion",
            input = "f0Input",
            adapter = PositiveProbabilityAdapter.class)
    RealScalar<? extends UnitInterval> initialProportion();

    @InputMapping(argument = "growthRate", input = "bInput")
    RealScalar<? extends PositiveReal> growthRate();

    @InputMapping(argument = "initialPopulationSize", input = "N0Input")
    RealScalar<? extends PositiveReal> initialPopulationSize();

    @InputMapping(argument = "ancestralPopulationSize", input = "NAInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "indicatorParameterInput",
            fallback = IndicatorFallback.class)
    RealScalar<? extends PositiveReal> ancestralPopulationSize();
}
