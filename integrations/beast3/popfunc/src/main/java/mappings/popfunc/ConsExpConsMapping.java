package mappings.popfunc;

import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;
import popfunc.beast.evolution.populationmodel.Cons_Exp_ConsGrowth;

@GeneratorMapping(
        component = "popfunc.functions.coalescent.constantExponentialConstantPopulationFunction",
        implementation = Cons_Exp_ConsGrowth.class,
        output = PopulationFunction.class)
public interface ConsExpConsMapping {

    @InputMapping(argument = "recentPopulationSize", input = "NCInput")
    RealScalar<? extends PositiveReal> recentPopulationSize();

    @InputMapping(argument = "growthRate", input = "rInput")
    RealScalar<? extends PositiveReal> growthRate();

    @InputMapping(argument = "growthStartAge", input = "xInput")
    RealScalar<? extends NonNegativeReal> growthStartAge();

    @InputMapping(argument = "growthEndAge", input = "tauInput")
    RealScalar<? extends NonNegativeReal> growthEndAge();
}
