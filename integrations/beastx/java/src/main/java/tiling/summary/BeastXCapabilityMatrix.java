package tiling.summary;

import java.util.List;

public class BeastXCapabilityMatrix {

    private final List<BeastXCapability> capabilities;

    private BeastXCapabilityMatrix(List<BeastXCapability> capabilities) {
        this.capabilities =
                List.copyOf(capabilities);
    }

    public static BeastXCapabilityMatrix current() {
        return new BeastXCapabilityMatrix(
                List.of(
                        capability(
                                "Input data",
                                "Nexus / FASTA / CSV / Newick / imported tree",
                                "yes",
                                "yes",
                                "yes",
                                "partial",
                                "partial",
                                "Nexus and Newick paths are used by representative and XML examples; XML export currently focuses on alignment/tree inputs needed by supported XML models."
                        ),
                        capability(
                                "Input data",
                                "dated tips and taxa metadata",
                                "yes",
                                "yes",
                                "yes",
                                "partial",
                                "partial",
                                "Dated-tip model construction is supported; XML coverage should remain explicit because BEAST X XML date/tip handling has stricter parser requirements."
                        ),
                        capability(
                                "Distributions",
                                "scalar priors",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Normal, LogNormal, Gamma, Exponential, Uniform, Beta and related scalar priors are covered through XML scalar-prior tests."
                        ),
                        capability(
                                "Distributions",
                                "simplex / Dirichlet priors",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "parse-level",
                                "Dirichlet prior XML is generated for simplex parameters; full run coverage depends on the model using that simplex in a supported likelihood."
                        ),
                        capability(
                                "Tree priors",
                                "Yule",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Used by strict-clock PhyloCTMC XML and representative models."
                        ),
                        capability(
                                "Tree priors",
                                "BirthDeath",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Parameterized BirthDeath XML is supported with BEAST X-compatible parameter domains."
                        ),
                        capability(
                                "Tree priors",
                                "Coalescent and population functions",
                                "yes",
                                "yes",
                                "yes",
                                "partial",
                                "partial",
                                "Constant/exponential/logistic/skyline model construction is covered; XML coverage should be expanded only for stable BEAST X XML parser paths."
                        ),
                        capability(
                                "Tree priors",
                                "FossilizedBirthDeath",
                                "yes",
                                "yes",
                                "yes",
                                "boundary",
                                "boundary",
                                "Object-level construction is supported; XML export should remain explicit because BEAST X FBD XML requires careful origin/sampling configuration."
                        ),
                        capability(
                                "Substitution models",
                                "JC69 / K80 / F81 / HKY / GTR",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Nucleotide PhyloCTMC XML supports fixed and parameterized substitution model components."
                        ),
                        capability(
                                "Substitution models",
                                "JTT / WAG / LG",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Protein XML requires BEAST X's 'amino acid' dataType string rather than 'aminoacid'."
                        ),
                        capability(
                                "Substitution models",
                                "Mk discrete traits",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Discrete trait PhyloCTMC/Mk support is covered by representative and XML examples."
                        ),
                        capability(
                                "Substitution models",
                                "GY94 codon",
                                "yes",
                                "yes",
                                "yes",
                                "component-only",
                                "blocked",
                                "Codon model components can be represented, but full XML run is intentionally blocked while BEAST X XML sequence parsing cannot safely materialize codon alignments from the current exported character representation."
                        ),
                        capability(
                                "Branch models",
                                "StrictClock",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Supported in object-level and XML-level PhyloCTMC models."
                        ),
                        capability(
                                "Branch models",
                                "RelaxedClock",
                                "yes",
                                "yes",
                                "yes",
                                "partial",
                                "beagle-dependent",
                                "Relaxed-clock XML run depends on BEAGLE/native likelihood availability and stable operator configuration."
                        ),
                        capability(
                                "Site models",
                                "default and gamma site model",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "XML currently emits BEAST X-compatible site model elements used by treeLikelihood."
                        ),
                        capability(
                                "PhyloCTMC",
                                "nucleotide full treeLikelihood",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "This is the core full-likelihood XML path."
                        ),
                        capability(
                                "PhyloCTMC",
                                "partitioned likelihoods",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Partitioned GTR/HKY XML with shared tree and clock is supported."
                        ),
                        capability(
                                "MCMC",
                                "chain length / seed / operators",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Automatic operator configuration is available for scalar, simplex, tree and clock-related parameters."
                        ),
                        capability(
                                "MCMC",
                                "screen/file/tree loggers",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "yes",
                                "Logger tiles are supported in both object-level and XML-level execution paths."
                        ),
                        capability(
                                "Runner",
                                "in-memory execution",
                                "not a tile",
                                "yes",
                                "yes",
                                "not applicable",
                                "not applicable",
                                "PhyloSpecRunner can build and execute BEAST X MCMC directly through Java objects."
                        ),
                        capability(
                                "Runner",
                                "XML export and XML execution",
                                "not a tile",
                                "yes",
                                "not applicable",
                                "yes",
                                "yes",
                                "PhyloSpecRunner can build XML run results from source strings or .phylospec files."
                        )
                )
        );
    }

    public List<BeastXCapability> capabilities() {
        return capabilities;
    }

    public String toMarkdown() {
        StringBuilder markdown =
                new StringBuilder();

        markdown.append("## Capability Matrix\n\n");
        markdown.append("| Area | Feature | Tile coverage | Model construction | In-memory MCMC | XML export | XML parse/run | Notes |\n");
        markdown.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");

        for (BeastXCapability capability : capabilities) {
            markdown.append(capability.toMarkdownRow());
        }

        return markdown.toString();
    }

    private static BeastXCapability capability(
            String area,
            String feature,
            String tileCoverage,
            String modelConstruction,
            String inMemoryMCMC,
            String xmlExport,
            String xmlParseRun,
            String notes
    ) {
        return new BeastXCapability(
                area,
                feature,
                tileCoverage,
                modelConstruction,
                inMemoryMCMC,
                xmlExport,
                xmlParseRun,
                notes
        );
    }
}
