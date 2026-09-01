package mappings.trees;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(
        component = "phylospec.functions.coalescent" + ".constantPopulationFunction",
        implementation = ConstantPopulation.class,
        output = PopulationFunction.class)
public interface ConstantPopulationMapping {

    @InputMapping(argument = "populationSize", input = "popSizeParameter")
    RealScalar<? extends PositiveReal> populationSize();
}
