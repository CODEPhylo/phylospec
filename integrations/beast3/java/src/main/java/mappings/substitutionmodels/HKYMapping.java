package mappings.substitutionmodels;

import adapters.FrequenciesAdapter;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(component = "phylospec.functions.substitution.hky", implementation = HKY.class)
public interface HKYMapping {

    @InputMapping(argument = "kappa", input = "kappaInput")
    RealScalar<PositiveReal> kappa();

    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput", adapter = FrequenciesAdapter.class)
    Simplex baseFrequencies();
}
