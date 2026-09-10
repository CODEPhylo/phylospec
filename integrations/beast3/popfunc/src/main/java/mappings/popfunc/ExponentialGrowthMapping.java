package mappings.popfunc;

import adapters.popfunc.AncestralIndicatorAdapter;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.ExponentialGrowth;

@GeneratorMapping(
        component = "phylospec.functions.coalescent.exponentialPopulationFunction",
        implementation = ExponentialGrowth.class,
        output = PopulationFunction.class)
public interface ExponentialGrowthMapping {

    @InputMapping(argument = "populationSize", input = "popSizeParameterInput")
    RealScalar<? extends PositiveReal> populationSize();

    @InputMapping(argument = "growthRate", input = "growthRateParameterInput")
    RealScalar<? extends Real> growthRate();

    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "ancestralPopulationParameterInput")
    @InputMapping(
            argument = "ancestralPopulationSize",
            input = "indicatorParameterInput",
            adapter = AncestralIndicatorAdapter.class)
    RealScalar<? extends PositiveReal> ancestralPopulationSize();
}
