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
                            "src/test/java/tiling/representative/hkyPhyloCTMC.phylospec",
                            "Shows stochastic HKY substitution parameters, a Yule tree prior, and an alignment likelihood."
                    ),
                    new ModelExample(
                            "GTR + parameter priors + PhyloCTMC",
                            "substitution model + sequence likelihood",
                            "src/test/java/tiling/representative/gtrPhyloCTMC.phylospec",
                            "Shows a richer nucleotide substitution model with multiple stochastic rate parameters."
                    ),
                    new ModelExample(
                            "Strict clock + clock-rate prior + PhyloCTMC + MCMC config",
                            "clock model + sequence likelihood + runner config",
                            "src/test/java/tiling/representative/strictClockPhyloCTMCWithMCMC.phylospec",
                            "Shows a strict-clock PhyloCTMC model with explicit chain length, screen logger, file logger, and tree logger."
                    ),
                    new ModelExample(
                            "Relaxed clock + PhyloCTMC",
                            "branch-rate model + sequence likelihood",
                            "src/test/java/tiling/representative/relaxedClockPhyloCTMC.phylospec",
                            "Shows branch-specific rate variation through a relaxed-clock model."
                    ),
                    new ModelExample(
                            "Discrete gamma/invariant site model + PhyloCTMC",
                            "site model + sequence likelihood",
                            "src/test/java/tiling/representative/siteModelPhyloCTMC.phylospec",
                            "Shows site-rate heterogeneity tiled into the sequence likelihood model."
                    ),
                    new ModelExample(
                            "Coalescent + stochastic exponential population function + PhyloCTMC",
                            "tree prior + demographic model + sequence likelihood",
                            "src/test/java/tiling/representative/coalescentExponentialPopulationPhyloCTMC.phylospec",
                            "Shows stochastic demographic parameters inside a coalescent tree prior."
                    ),
                    new ModelExample(
                            "Calibrated Yule + PhyloCTMC",
                            "tree prior + calibration prior + sequence likelihood",
                            "src/test/java/tiling/representative/calibratedYulePhyloCTMC.phylospec",
                            "Shows a tree prior combined with a root-age calibration prior."
                    ),
                    new ModelExample(
                            "Partitioned subset PhyloCTMC",
                            "partitioned data + multiple sequence likelihoods",
                            "src/test/java/tiling/representative/partitionedSubsetPhyloCTMC.phylospec",
                            "Shows that one alignment can be subset into multiple PhyloCTMC likelihood components."
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
        report.append("| Scalar/vector stochastic parameters | Supported |\n");
        report.append("| Deterministic calculations | Supported through RPNcalculatorStatistic calculation nodes |\n");
        report.append("| Prior distributions | Normal, LogNormal, LogNormalRealSpace, Exponential, Gamma, Beta, Uniform, Cauchy, Poisson, DiscreteUniform, Dirichlet, Offset |\n");
        report.append("| Input | Nexus, FASTA, Newick, existing tree object, parser helpers, alignment subset |\n");
        report.append("| Tree priors | Yule, BirthDeath, Coalescent, constant population, exponential population |\n");
        report.append("| Calibration priors | Root-age calibration and MRCA-style calibration support |\n");
        report.append("| Substitution models | JC69, K80, F81, HKY, GTR, JTT, WAG, LG |\n");
        report.append("| Site models | Discrete gamma / invariant-site style site-rate model |\n");
        report.append("| Branch-rate models | Strict clock, relaxed clock |\n");
        report.append("| Sequence likelihood | PhyloCTMC tiled into BeastXPhyloCTMCLikelihoodSpec |\n");
        report.append("| Likelihood materialization | Optional BeastXPhyloCTMCLikelihoodSpec -> BeagleTreeLikelihood path; requires native BEAGLE |\n");
        report.append("| MCMC operators | Default parameter operators and tree operators generated from BeastXState |\n");
        report.append("| MCMC runner config | chainLength, screenLogger, fileLogger, treeLogger |\n");
        report.append("| MCMC execution | Prior-only short MCMC run writes real samples; deterministic calculation nodes can be logged; PhyloCTMC MCMC run is BEAGLE-dependent |\n\n");

        report.append("## Non-trivial Model Examples\n\n");
        report.append("| Model | Category | State nodes | Calculation nodes | Parameter priors | Tree priors | Calibration priors | Likelihoods | Operators | MCMC config | Interpretation |\n");
        report.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");

        int builtExamples =
                0;

        int examplesWithMCMCConfig =
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

            if (hasMCMCConfig(summary)) {
                examplesWithMCMCConfig++;
            }

            report.append("| ")
                    .append(escape(example.name))
                    .append(" | ")
                    .append(escape(example.category))
                    .append(" | ")
                    .append(escape(summary.stateNodes.toString()))
                    .append(" | ")
                    .append(escape(summary.calculationNodes.toString()))
                    .append(" | ")
                    .append(escape(summary.parameterPriors.toString()))
                    .append(" | ")
                    .append(escape(summary.treePriors.toString()))
                    .append(" | ")
                    .append(escape(summary.calibrationPriors.toString()))
                    .append(" | ")
                    .append(escape(summary.likelihoods.toString()))
                    .append(" | ")
                    .append(escape(summary.operators.toString()))
                    .append(" | ")
                    .append(escape(formatMCMCConfig(summary)))
                    .append(" | ")
                    .append(escape(example.interpretation))
                    .append(" |\n");

            builtExamples++;
        }

        BeastXModel deterministicModel =
                buildModelFromSource(
                        """
                        PositiveReal x ~ LogNormal(logMean=0.0, logSd=1.0)
                        PositiveReal y ~ LogNormal(logMean=0.0, logSd=1.0)
                        Real z = x + y

                        mcmc {
                            Integer chainLength = 5

                            Logger fileLogger = fileLogger(
                                logEvery=1,
                                file="target/beastx-results/deterministic-rpn.log",
                                parameters=[z]
                            )
                        }
                        """
                );

        BeastXModelSummary deterministicSummary =
                BeastXModelSummary.from(deterministicModel);

        assertTrue(deterministicSummary.calculationNodes.contains("z"));

        report.append("\n## Deterministic Calculation Example\n\n");
        report.append("This small model checks the language-level deterministic calculation path separately from sequence likelihoods.\n\n");
        report.append("| Feature | BeastX result |\n");
        report.append("| --- | --- |\n");
        report.append("| State nodes | ")
                .append(escape(deterministicSummary.stateNodes.toString()))
                .append(" |\n");
        report.append("| Calculation nodes | ")
                .append(escape(deterministicSummary.calculationNodes.toString()))
                .append(" |\n");
        report.append("| Calculation node types | ")
                .append(escape(deterministicSummary.calculationNodeTypes.toString()))
                .append(" |\n");
        report.append("| Parameter priors | ")
                .append(escape(deterministicSummary.parameterPriors.toString()))
                .append(" |\n");
        report.append("| MCMC config | ")
                .append(escape(formatMCMCConfig(deterministicSummary)))
                .append(" |\n\n");

        report.append("## Runtime Validation\n\n");
        report.append("| Runtime path | Status |\n");
        report.append("| --- | --- |\n");
        report.append("| PhyloSpec -> BeastXState | Implemented and covered by script-state tests |\n");
        report.append("| PhyloSpec -> BeastXModel | Implemented and covered by runner/model tests |\n");
        report.append("| Deterministic expression -> RPNcalculatorStatistic | Implemented and covered by RPN smoke tests |\n");
        report.append("| Deterministic calculation logging | Implemented; MCMC fileLogger can log calculation nodes |\n");
        report.append("| Prior-only MCMC execution | Implemented; short chain writes multiple logger samples |\n");
        report.append("| fileLogger/treeLogger output | Implemented; logger smoke tests write non-empty log/tree files |\n");
        report.append("| PhyloCTMC materialization | Implemented as optional BeagleTreeLikelihood materialization path |\n");
        report.append("| PhyloCTMC MCMC execution | Implemented as BEAGLE-dependent smoke test; skipped when native BEAGLE is unavailable |\n\n");

        report.append("## Summary\n\n");
        report.append("Curated non-trivial BeastX model examples: ")
                .append(builtExamples)
                .append("\n\n");

        report.append("Examples with explicit MCMC configuration: ")
                .append(examplesWithMCMCConfig)
                .append("\n\n");

        report.append("These examples show that the BeastX backend can tile PhyloSpec models across multiple model axes: ");
        report.append("tree priors, calibration priors, demographic models, substitution models, site-rate models, ");
        report.append("branch-rate models, sequence likelihoods, parameter priors, deterministic calculations, ");
        report.append("default MCMC operators, and MCMC logging configuration.\n\n");

        report.append("Current engine-level limitation: full PhyloCTMC likelihood evaluation and PhyloCTMC MCMC execution ");
        report.append("require the native BEAGLE library to be installed and available on java.library.path. ");
        report.append("On machines without BEAGLE, those tests are skipped rather than failed.\n");

        Files.createDirectories(REPORT_PATH.getParent());
        Files.writeString(REPORT_PATH, report.toString(), StandardCharsets.UTF_8);

        assertEquals(EXAMPLES.size(), builtExamples);
        assertTrue(
                examplesWithMCMCConfig >= 1,
                "Expected at least one representative model with explicit MCMC configuration."
        );
    }

    private BeastXModel buildModelFromFile(String path) throws Exception {
        String source =
                readSource(path);

        return buildModelFromSource(source);
    }

    private BeastXModel buildModelFromSource(String source) throws Exception {
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

    private boolean hasMCMCConfig(BeastXModelSummary summary) {
        return summary.chainLength != 1
                || !summary.screenLoggers.isEmpty()
                || !summary.fileLoggers.isEmpty()
                || !summary.treeLoggers.isEmpty();
    }

    private String formatMCMCConfig(BeastXModelSummary summary) {
        if (!hasMCMCConfig(summary)) {
            return "none";
        }

        return "chainLength="
                + summary.chainLength
                + "; screenLoggers="
                + summary.screenLoggers
                + "; fileLoggers="
                + summary.fileLoggers
                + "; treeLoggers="
                + summary.treeLoggers;
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