package mappings.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.tree.coalescent.ExponentialGrowth;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(
        component = "phylospec.functions.coalescent.exponentialPopulationFunction",
        implementation = ExponentialGrowth.class,
        output = PopulationFunction.class)
public interface ExponentialPopulationMapping {

    @InputMapping(argument = "populationSize", input = "popSizeParameterInput")
    RealScalar<? extends PositiveReal> populationSize();

    @InputMapping(argument = "growthRate", input = "growthRateParameterInput")
    RealScalar<? extends Real> growthRate();
}
