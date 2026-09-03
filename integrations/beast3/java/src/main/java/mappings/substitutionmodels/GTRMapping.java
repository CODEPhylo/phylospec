package mappings.substitutionmodels;

import adapters.FrequenciesAdapter;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.substitutionmodel.GTR;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

@GeneratorMapping(
        component = "phylospec.functions.substitution.gtr",
        implementation = GTR.class,
        arguments = {"rateAC", "rateAG", "rateAT", "rateCG", "rateCT", "rateGT", "baseFrequencies"})
public interface GTRMapping {

    @InputMapping(argument = "rateAC", input = "rateACInput")
    RealScalar<PositiveReal> rateAC();

    @InputMapping(argument = "rateAG", input = "rateAGInput")
    RealScalar<PositiveReal> rateAG();

    @InputMapping(argument = "rateAT", input = "rateATInput")
    RealScalar<PositiveReal> rateAT();

    @InputMapping(argument = "rateCG", input = "rateCGInput")
    RealScalar<PositiveReal> rateCG();

    @InputMapping(argument = "rateCT", input = "rateCTInput")
    RealScalar<PositiveReal> rateCT();

    @InputMapping(argument = "rateGT", input = "rateGTInput")
    RealScalar<PositiveReal> rateGT();

    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput", adapter = FrequenciesAdapter.class)
    Simplex baseFrequencies();
}
