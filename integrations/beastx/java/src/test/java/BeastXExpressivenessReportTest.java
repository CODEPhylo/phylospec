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

            "src/test/java/tiling/representative/showcase/codonSelectionGY94PhyloCTMC.phylospec",
            "src/test/java/tiling/representative/showcase/datedTipFBDRelaxedClockGTR.phylospec",
            "src/test/java/tiling/representative/showcase/jointMolecularTraitMkPhyloCTMC.phylospec",
            "src/test/java/tiling/representative/showcase/partitionedGtrHkySiteClockMCMC.phylospec",
            "src/test/java/tiling/representative/showcase/priorOnlyFBDMCMC.phylospec",
            "src/test/java/tiling/representative/showcase/skylineHKYStrictClockMCMC.phylospec"
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
        report.append("| Input data | Nexus, FASTA, Newick, imported tree, subset alignments, dated tips, discrete traits from taxa |\n");
        report.append("| Core language / data handling | Assignments, draws, tree draws, vectors, lists, indexed statements, indexed vector access, ranges |\n");
        report.append("| Scalar and vector functions | repeat, range, linspace, log, exp, sqrt, sum, numSites, numTaxa, numBranches, numRows, numCols, rootAge, MRCA, taxon age access |\n");
        report.append("| Prior distributions | Uniform, Normal, LogNormal, LogNormalRealSpace, Exponential, Gamma, Beta, Dirichlet, Cauchy, Poisson, Bernoulli, Binomial, Categorical, Geometric, DiscreteUniform, MultivariateNormal, IID, Truncated, Offset |\n");
        report.append("| Tree priors | Yule, BirthDeath, FossilizedBirthDeath, Coalescent, SkylineCoalescent, and coalescent population functions including constant, exponential, logistic, and compound population functions |\n");
        report.append("| Substitution models | JC69, K80, F81, HKY, GTR, JTT, WAG, LG, Mk, GY94 codon model |\n");
        report.append("| Site models | SiteModel, drawn site rates, discrete gamma / invariant-site style site-rate vectors |\n");
        report.append("| Clock / branch models | StrictClock, RelaxedClock, drawn branch rates |\n");
        report.append("| Observations and calibration | observed-as alignment/scalar/int handling, root-age calibration, MRCA calibration, dated-tip handling |\n");
        report.append("| MCMC configuration | chainLength, randomSeed, defaultLogEvery, outputPrefix, screenLogger, fileLogger, treeLogger, selected parameter logging, operator tuning settings |\n");
        report.append("| MCMC operators | Automatic parameter operators, tree operators, simplex operators, relaxed-clock operators, and tree-clock coupled UpDown operators with inspectable details |\n");
        report.append("| Runtime execution | Model construction, materialized model construction where supported, MCMC construction, and direct MCMC execution for supported non-BEAGLE workflows |\n\n");

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
        report.append("## Showcase examples\n\n");
        report.append("| Example | What it demonstrates |\n");
        report.append("| --- | --- |\n");
        report.append("| `partitionedGtrHkySiteClockMCMC.phylospec` | Partitioned molecular model with GTR/HKY partitions, site-rate variation, shared tree/clock, MCMC loggers |\n");
        report.append("| `datedTipFBDRelaxedClockGTR.phylospec` | Dated-tip FBD model with relaxed clock, GTR, serial sampling, and dated-tip parsing from Nexus |\n");
        report.append("| `jointMolecularTraitMkPhyloCTMC.phylospec` | Joint molecular and discrete-trait likelihoods using HKY and Mk on a shared tree |\n");
        report.append("| `codonSelectionGY94PhyloCTMC.phylospec` | Codon substitution model using GY94 with codon frequencies and omega/kappa parameters |\n");
        report.append("| `skylineHKYStrictClockMCMC.phylospec` | Skyline coalescent model with HKY, strict clock, MCMC configuration, and loggers |\n");
        report.append("| `priorOnlyFBDMCMC.phylospec` | Prior-only FBD MCMC workflow with parameter and tree logging |\n\n");

        report.append("## Operator detail examples\n\n");
        report.append("The BEAST X backend exposes operator-level summaries so representative models can be inspected beyond class names.\n\n");

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

        report.append("## Notes\n\n");
        report.append("The table above is construction-level coverage. It checks that representative PhyloSpec scripts can be tiled and converted into BEAST X model objects.\n\n");
        report.append("BEAGLE-dependent PhyloCTMC materialization is environment-sensitive because it requires the native BEAGLE library. Those tests may be skipped when hmsbeagle is unavailable, while construction-level coverage remains testable on a plain Java setup.\n");

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