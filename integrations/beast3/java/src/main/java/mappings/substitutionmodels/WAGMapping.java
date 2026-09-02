package mappings.substitutionmodels;

import adapters.FrequenciesAdapter;
import beast.base.spec.evolution.substitutionmodel.WAG;
import beast.base.spec.type.Simplex;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(component = "phylospec.functions.substitution.wag", implementation = WAG.class)
public interface WAGMapping {

    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput", adapter = FrequenciesAdapter.class)
    Simplex baseFrequencies();
}
