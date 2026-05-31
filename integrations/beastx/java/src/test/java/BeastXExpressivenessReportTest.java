import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXExpressivenessReportTest {

    private static final Path REPORT_PATH =
            Path.of("target", "beastx-results", "beastx-expressiveness-report.md");

    private static final List<String> EXAMPLES = List.of(
            "src/test/java/tiling/representative/coverage/hkyPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/gtrPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/strictClockPhyloCTMCWithMCMC.phylospec",
            "src/test/java/tiling/representative/coverage/datedTipStrictClockPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/datedTipBirthDeathPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/relaxedClockPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/siteModelPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/coalescentExponentialPopulationPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/coalescentLogisticPopulationPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/calibratedYulePhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/mrcaCalibratedYulePhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/partitionedSubsetPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/coverage/binaryTraitMkPhyloCTMC.phylospec",

            "src/test/java/tiling/representative/showcase/datedTipFBDRelaxedClockGTR.phylospec",
            "src/test/java/tiling/representative/showcase/jointMolecularTraitMkPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/showcase/partitionedGtrHkySiteClockMCMC.phylospec",
            "src/test/java/tiling/representative/showcase/codonSelectionGY94PhyloCTMC.phylospec",
            "src/test/java/tiling/representative/showcase/priorOnlyFBDMCMC.phylospec"
    );

    @Test
    public void writesBeastXExpressivenessReport() throws Exception {
        Files.createDirectories(REPORT_PATH.getParent());

        StringBuilder report =
                new StringBuilder();

        report.append("# PhyloSpec BEAST X Expressiveness Report\n\n");
        report.append("This report summarizes the current BEAST X integration coverage in PhyloSpec.\n\n");

        report.append("## Supported model components\n\n");
        report.append("| Area | Current BEAST X support |\n");
        report.append("| --- | --- |\n");
        report.append("| Input data | Nexus, FASTA, Newick, imported tree, subset alignments, dated tips, discrete traits |\n");
        report.append("| Substitution models | HKY, GTR, JC69, Mk, GY94 codon model |\n");
        report.append("| Site models | Gamma shape site model |\n");
        report.append("| Clock models | Strict clock and relaxed clock category model |\n");
        report.append("| Tree priors | Yule, BirthDeath, FossilizedBirthDeath, Coalescent with constant, exponential, logistic, skyline, and compound population functions |\n");
        report.append("| Calibration | Root age and MRCA calibration priors |\n");
        report.append("| Priors | Uniform, Normal, LogNormal, Exponential, Gamma, Beta, Dirichlet, Cauchy, Bernoulli, Binomial, Categorical, Geometric, MultivariateNormal, IID |\n");
        report.append("| Deterministic calculations | RPN-style scalar calculations, indexed calculations, matrix dimensions, log, exp, sqrt |\n");
        report.append("| MCMC configuration | Chain length, screen logger, file logger, tree logger, selected parameter logging |\n");
        report.append("| MCMC operators | Automatic parameter, tree, simplex, relaxed-clock, and tree-clock coupled operators with inspectable operator details |\n");
        report.append("| Runtime execution | Model construction, MCMC construction, and direct MCMC execution through PhyloSpecRunner |\n\n");

        report.append("## Representative examples\n\n");
        report.append("| Example | State nodes | Priors | Trees | Tree priors | Calibrations | Likelihoods | Operators | MCMC |\n");
        report.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");

        int builtExamples =
                0;

        int examplesWithMCMCConfig =
                0;

        List<ExampleSummary> summaries =
                new ArrayList<>();

        for (String example : EXAMPLES) {
            Path path =
                    Path.of(example);

            assertTrue(
                    Files.exists(path),
                    "Missing example file: " + example
            );

            BeastXModel model =
                    buildModelFromFile(path);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            summaries.add(new ExampleSummary(path, summary));

            boolean hasMCMCConfig =
                    hasMCMCConfig(summary);

            if (hasMCMCConfig) {
                examplesWithMCMCConfig++;
            }

            report.append("| ")
                    .append(path.getFileName())
                    .append(" | ")
                    .append(summary.stateNodes.size())
                    .append(" | ")
                    .append(summary.parameterPriors.size())
                    .append(" | ")
                    .append(summary.treeModels.size())
                    .append(" | ")
                    .append(summary.treePriors.size())
                    .append(" | ")
                    .append(summary.calibrationPriors.size())
                    .append(" | ")
                    .append(summary.likelihoods.size())
                    .append(" | ")
                    .append(summary.operators.size())
                    .append(" | ")
                    .append(hasMCMCConfig ? "yes" : "no")
                    .append(" |\n");

            builtExamples++;
        }

        report.append("\n");
        report.append("## Operator detail examples\n\n");
        report.append("The BEAST X backend now exposes operator-level summaries so representative models can be inspected beyond class names.\n\n");

        for (ExampleSummary exampleSummary : summaries) {
            if (!isShowcaseExample(exampleSummary.path)) {
                continue;
            }

            BeastXModelSummary summary =
                    exampleSummary.summary;

            report.append("### ")
                    .append(exampleSummary.path.getFileName())
                    .append("\n\n");

            if (summary.operatorDetails.isEmpty()) {
                report.append("No MCMC operators were constructed for this example.\n\n");
                continue;
            }

            for (String operatorDetail : summary.operatorDetails) {
                report.append("- `")
                        .append(operatorDetail)
                        .append("`\n");
            }

            report.append("\n");
        }

        report.append("## Interpretation\n\n");
        report.append("The BEAST X backend now supports non-trivial Bayesian phylogenetic model construction, ");
        report.append("including molecular substitution models, dated-tip tree priors, fossilized birth-death priors, ");
        report.append("partitioned likelihoods, discrete trait Mk models, codon substitution models, MCMC configuration, ");
        report.append("operator schedule inspection, and direct runtime MCMC execution for supported non-BEAGLE workflows.\n\n");

        report.append("BEAGLE-dependent PhyloCTMC materialization is environment-sensitive because it requires the native ");
        report.append("BEAGLE library. Those tests may be skipped when hmsbeagle is unavailable, while construction-level ");
        report.append("coverage remains testable on a plain Java setup.\n");

        Files.writeString(
                REPORT_PATH,
                report.toString(),
                StandardCharsets.UTF_8
        );

        assertEquals(
                EXAMPLES.size(),
                builtExamples
        );

        assertTrue(
                examplesWithMCMCConfig >= 1,
                "Expected at least one representative model with MCMC config."
        );

        assertTrue(
                summaries.stream().anyMatch(summary -> !summary.summary.operatorDetails.isEmpty()),
                "Expected at least one representative model with operator details."
        );

        assertTrue(Files.exists(REPORT_PATH));
        assertTrue(Files.size(REPORT_PATH) > 0);
    }

    private static BeastXModel buildModelFromFile(Path path) throws Exception {
        return new PhyloSpecRunner(readSource(path)).buildModel("test");
    }

    private static String readSource(Path path) throws Exception {
        List<String> lines =
                Files.readAllLines(path, StandardCharsets.UTF_8);

        return String.join(System.lineSeparator(), stripExpectedBlocks(lines));
    }

    private static List<String> stripExpectedBlocks(List<String> lines) {
        List<String> sourceLines =
                new ArrayList<>();

        boolean insideExpectedBlock =
                false;

        for (String line : lines) {
            if (line.trim().startsWith("// EXPECTED")) {
                insideExpectedBlock = !insideExpectedBlock;
                continue;
            }

            if (!insideExpectedBlock) {
                sourceLines.add(line);
            }
        }

        return sourceLines;
    }

    private static boolean hasMCMCConfig(BeastXModelSummary summary) {
        return summary.chainLength != 1
                || !summary.screenLoggers.isEmpty()
                || !summary.fileLoggers.isEmpty()
                || !summary.treeLoggers.isEmpty();
    }

    private static boolean isShowcaseExample(Path path) {
        return path.toString().contains("showcase");
    }

    private record ExampleSummary(
            Path path,
            BeastXModelSummary summary
    ) {}
}