package mappings.popfunc;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.type.IntScalar;
import java.util.List;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.StochasticVariableSelection;

@GeneratorMapping(
        component = "popfunc.functions.coalescent.stochasticPopulationSelection",
        implementation = StochasticVariableSelection.class,
        output = PopulationFunction.class)
public interface StochasticPopulationSelectionMapping {

    @InputMapping(argument = "indicator", input = "indicatorInput")
    IntScalar<? extends NonNegativeInt> indicator();

    @InputMapping(argument = "models", input = "modelsInput")
    List<PopulationFunction> models();
}
