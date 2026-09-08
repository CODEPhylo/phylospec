package mappings.substitutionmodels;

import beast.base.spec.evolution.substitutionmodel.JTT;
import beast.base.spec.type.Simplex;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(component = "phylospec.functions.substitution.jtt", implementation = JTT.class)
public interface JTTMapping {

    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput")
    Simplex baseFrequencies();
}
