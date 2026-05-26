import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXExpressivenessReportTest {

    private static final Path REPORT_PATH =
            Path.of("target", "beastx-results", "beastx-expressiveness-report.md");

    private static final List<ModelExample> EXAMPLES =
            List.of(
                    new ModelExample(
                            "HKY + parameter priors + PhyloCTMC",
                            "substitution model + sequence likelihood",
                            "src/test/java/tiling/phyloctmc/withInlinePriors.phylospec",
                            "Shows stochastic substitution parameters, a Yule tree prior, and an alignment likelihood."
                    ),
                    new ModelExample(
                            "Strict clock + clock-rate prior + PhyloCTMC",
                            "clock model + sequence likelihood",
                            "src/test/java/tiling/phyloctmc/withStrictClockPrior.phylospec",
                            "Shows a stochastic molecular clock rate connected to a PhyloCTMC likelihood."
                    ),
                    new ModelExample(
                            "Discrete gamma/invariant site model + PhyloCTMC",
                            "site model + sequence likelihood",
                            "src/test/java/tiling/phyloctmc/withDiscreteGammaInv.phylospec",
                            "Shows site-rate heterogeneity tiled into the sequence likelihood model."
                    ),
                    new ModelExample(
                            "Coalescent + stochastic exponential population function",
                            "tree prior + demographic model",
                            "src/test/java/tiling/treepriors/coalescentWithExponentialPopulationFunctionPrior.phylospec",
                            "Shows stochastic demographic parameters inside a coalescent tree prior."
                    ),
                    new ModelExample(
                            "Relaxed clock",
                            "branch-rate model",
                            "src/test/java/tiling/branchmodels/relaxedClock.phylospec",
                            "Shows branch-specific rate variation through a relaxed-clock model."
                    )
            );

    @Test
    public void writesBeastXExpressivenessReport() throws Exception {
        StringBuilder report =
                new StringBuilder();

        report.append("# BeastX PhyloSpec Tiling Expressiveness Report\n\n");

        report.append("This report is generated from BeastX PhyloSpec test models. ");
        report.append("It summarizes what each PhyloSpec model tiles into on the BeastX backend.\n\n");

        report.append("## Current BeastX Coverage\n\n");
        report.append("| Component axis | Current BeastX support |\n");
        report.append("| --- | --- |\n");
        report.append("| Scalar/vector stochastic parameters | yes |\n");
        report.append("| Prior distributions | Normal, LogNormal, LogNormalRealSpace, Exponential, Gamma, Beta, Uniform, Cauchy, Poisson, DiscreteUniform, Dirichlet, Offset |\n");
        report.append("| Tree priors | Yule, BirthDeath, Coalescent, constant population, exponential population |\n");
        report.append("| Substitution models | JC69, K80, F81, HKY, GTR, JTT, WAG, LG |\n");
        report.append("| Site models | discrete gamma / invariant-site style site-rate model |\n");
        report.append("| Branch-rate models | strict clock, relaxed clock |\n");
        report.append("| Input | Nexus, FASTA, Newick, existing tree object |\n");
        report.append("| Sequence likelihood | PhyloCTMC likelihood specification |\n");
        report.append("| Runtime structure | BeastXState, prior, likelihood, posterior, default parameter/tree operators |\n\n");

        report.append("## Non-trivial Model Examples\n\n");
        report.append("| Model | Category | State nodes | Parameter priors | Tree priors | Likelihoods | Operators | Interpretation |\n");
        report.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");

        int builtExamples =
                0;

        for (ModelExample example : EXAMPLES) {
            assertTrue(
                    Files.exists(Path.of(example.path)),
                    "Missing example file: " + example.path
            );

            BeastXModel model =
                    buildModelFromFile(example.path);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            report.append("| ")
                    .append(escape(example.name))
                    .append(" | ")
                    .append(escape(example.category))
                    .append(" | ")
                    .append(escape(summary.stateNodes.toString()))
                    .append(" | ")
                    .append(escape(summary.parameterPriors.toString()))
                    .append(" | ")
                    .append(escape(summary.treePriors.toString()))
                    .append(" | ")
                    .append(escape(summary.likelihoods.toString()))
                    .append(" | ")
                    .append(escape(summary.operators.toString()))
                    .append(" | ")
                    .append(escape(example.interpretation))
                    .append(" |\n");

            builtExamples++;
        }

        report.append("\n## Summary\n\n");
        report.append("Curated non-trivial BeastX model examples: ")
                .append(builtExamples)
                .append("\n\n");

        report.append("These examples show that the BeastX backend can tile PhyloSpec models across multiple model axes: ");
        report.append("tree priors, demographic models, substitution models, site-rate models, branch-rate models, sequence likelihoods, ");
        report.append("parameter priors, and default MCMC operators.\n\n");

        report.append("Current limitation: PhyloCTMC is tiled into a BeastX likelihood specification, ");
        report.append("but full BeagleTreeLikelihood materialization and complete sequence-likelihood MCMC execution are still ongoing.\n");

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report.toString(), StandardCharsets.UTF_8);

        assertEquals(EXAMPLES.size(), builtExamples);
    }

    private BeastXModel buildModelFromFile(String path) throws Exception {
        String source =
                readSource(path);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        return runner.buildModel("test");
    }

    private String readSource(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }

    private String escape(String value) {
        return value.replace("|", "\\|");
    }

    private static class ModelExample {
        private final String name;
        private final String category;
        private final String path;
        private final String interpretation;

        private ModelExample(
                String name,
                String category,
                String path,
                String interpretation
        ) {
            this.name =
                    name;

            this.category =
                    category;

            this.path =
                    path;

            this.interpretation =
                    interpretation;
        }
    }
}
