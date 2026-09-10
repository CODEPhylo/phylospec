package mappings.popfunc;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.ConstantGrowth;

@GeneratorMapping(
        component = "phylospec.functions.coalescent.constantPopulationFunction",
        implementation = ConstantGrowth.class,
        output = PopulationFunction.class)
public interface ConstantGrowthMapping {

    @InputMapping(argument = "populationSize", input = "popSizeParameterInput")
    RealScalar<? extends PositiveReal> populationSize();
}
