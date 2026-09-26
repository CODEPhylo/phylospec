package mappings.substitutionmodels;

import org.phylospec.annotations.GeneratorMapping;
import org.phylospec.annotations.InputMapping;

/**
 * PhyloSpec mappings for BEAST 3 core substitution models.
 *
 * <p>Inputs that follow the {@code argument -> argumentInput} convention (for example
 * {@code kappa -> kappaInput}, {@code rateAC -> rateACInput}) are matched automatically; only
 * {@code baseFrequencies -> frequenciesInput} is declared explicitly. Implementations are written
 * as fully qualified names because nested declarations share their simple name with the mapped
 * BEAST class.
 */
public final class SubstitutionModelMappings {

    private SubstitutionModelMappings() {}

    @GeneratorMapping(
            component = "phylospec.functions.substitution.gtr",
            implementation = beast.base.spec.evolution.substitutionmodel.GTR.class,
            arguments = {"rateAC", "rateAG", "rateAT", "rateCG", "rateCT", "rateGT", "baseFrequencies"})
    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput")
    public static final class GTR {}

    @GeneratorMapping(
            component = "phylospec.functions.substitution.hky",
            implementation = beast.base.spec.evolution.substitutionmodel.HKY.class)
    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput")
    public static final class HKY {}

    @GeneratorMapping(
            component = "phylospec.functions.substitution.jc69",
            implementation = beast.base.spec.evolution.substitutionmodel.JukesCantor.class)
    public static final class JC69 {}

    @GeneratorMapping(
            component = "phylospec.functions.substitution.jtt",
            implementation = beast.base.spec.evolution.substitutionmodel.JTT.class)
    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput")
    public static final class JTT {}

    @GeneratorMapping(
            component = "phylospec.functions.substitution.wag",
            implementation = beast.base.spec.evolution.substitutionmodel.WAG.class)
    @InputMapping(argument = "baseFrequencies", input = "frequenciesInput")
    public static final class WAG {}
}
