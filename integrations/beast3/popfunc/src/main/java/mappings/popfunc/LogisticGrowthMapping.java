package mappings.popfunc;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.LogisticGrowth;

@GeneratorMapping(
        component = "phylospec.functions.coalescent.logisticPopulationFunction",
        implementation = LogisticGrowth.class,
        output = PopulationFunction.class)
public interface LogisticGrowthMapping {

    @InputMapping(argument = "inflectionAge", input = "t50Input")
    RealScalar<? extends NonNegativeReal> inflectionAge();

    @InputMapping(argument = "carryingCapacity", input = "nCarryingCapacityInput")
    RealScalar<? extends PositiveReal> carryingCapacity();

    @InputMapping(argument = "growthRate", input = "bInput")
    RealScalar<? extends NonNegativeReal> growthRate();
}
