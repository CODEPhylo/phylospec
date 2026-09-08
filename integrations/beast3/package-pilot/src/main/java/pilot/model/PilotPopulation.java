package pilot.model;

import beast.base.core.Input;
import beast.base.evolution.tree.coalescent.PopulationFunction;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.type.RealScalar;
import java.util.List;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(
        component = "phylospec.functions.coalescent.constantPopulationFunction",
        output = PopulationFunction.class)
public final class PilotPopulation extends PopulationFunction.Abstract {

    @InputMapping(argument = "populationSize")
    public final Input<RealScalar<? extends PositiveReal>> populationSize =
            new Input<>(
                    "populationSize",
                    "constant population size",
                    Input.Validate.REQUIRED);

    public PilotPopulation() {}

    @Override
    public List<String> getParameterIds() {
        return List.of();
    }

    @Override
    public double getPopSize(double time) {
        return populationSize.get().get();
    }

    @Override
    public double getIntensity(double time) {
        return time / populationSize.get().get();
    }

    @Override
    public double getInverseIntensity(double intensity) {
        return intensity * populationSize.get().get();
    }
}
