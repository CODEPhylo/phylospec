import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.summary.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXCoverageTableTest {

    private static final Path REPORT_PATH =
            Path.of("target", "beastx-results", "beastx-coverage-tables.md");

    private static final List<CoverageExample> SELECTED_EXAMPLES =
            List.of(
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "showcase", "datedTipFBDRelaxedClockGTR.phylospec"),
                            "dated tips + FBD + relaxed clock + GTR",
                            "model construction"
                    ),
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "showcase", "partitionedGtrHkySiteClockMCMC.phylospec"),
                            "partitioned GTR/HKY model + site rates + MCMC loggers",
                            "model construction + MCMC config"
                    ),
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "showcase", "jointMolecularTraitMkPhyloCTMC.phylospec"),
                            "joint molecular and discrete-trait likelihoods",
                            "model construction"
                    ),
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "showcase", "skylineHKYStrictClockMCMC.phylospec"),
                            "skyline coalescent + HKY + strict clock + MCMC",
                            "model construction + MCMC config"
                    ),
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "showcase", "codonSelectionGY94PhyloCTMC.phylospec"),
                            "GY94 codon model with omega/kappa parameters",
                            "model construction"
                    ),
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "coverage", "mrcaCalibratedYulePhyloCTMC.phylospec"),
                            "MRCA calibration + Yule + PhyloCTMC",
                            "model construction"
                    ),
                    new CoverageExample(
                            Path.of("src", "test", "java", "tiling", "representative", "coverage", "coalescentLogisticPopulationPhyloCTMC.phylospec"),
                            "coalescent model with logistic population function",
                            "model construction"
                    )
            );

    @Test
    public void writesCurrentBeastXCoverageTables() throws Exception {
        Files.createDirectories(REPORT_PATH.getParent());

        String report =
                reportHeader()
                        + "\n"
                        + componentCoverageTable()
                        + "\n"
                        + selectedModelCoverageTable()
                        + "\n"
                        + xmlCoverageTable()
                        + "\n"
                        + beastXXmlExportQuestionTable();

        Files.writeString(
                REPORT_PATH,
                report,
                StandardCharsets.UTF_8
        );

        assertTrue(
                SELECTED_EXAMPLES.size() >= 6,
                "Expected several selected representative models."
        );

        assertTrue(
                report.contains("datedTipFBDRelaxedClockGTR"),
                "Expected the selected model coverage table to include the dated-tip FBD showcase model."
        );

        assertTrue(Files.exists(REPORT_PATH));
        assertTrue(Files.size(REPORT_PATH) > 0);
    }

    private static String reportHeader() {
        return """
                # BEAST X Coverage Tables

                Compact coverage snapshot for the current PhyloSpec BEAST X backend. The model table lists selected examples, not every test fixture.
                """;
    }

    private static String componentCoverageTable() {
        return """
                ## Component Coverage

                | Area | Current BEAST X tile coverage |
                | --- | --- |
                | Input data | Nexus, FASTA, CSV/table data, Newick, imported trees, alignment subsets, dated tips, discrete/continuous traits |
                | Core language/data handling | Assignments, draws, tree draws, vectors, lists, ranges, indexed statements, indexed access |
                | Functions/calculations | repeat, range, linspace, log/exp/sqrt, sum, dimensions/counts, taxa/tree accessors, MRCA/root-age/taxon-age helpers, RPN calculations |
                | Scalar/vector priors | Uniform, Normal, LogNormal, LogNormalRealSpace, Exponential, Gamma, Beta, Dirichlet, Cauchy, Poisson, Bernoulli, Binomial, Categorical, Geometric, DiscreteUniform, MultivariateNormal, IID, Truncated, Offset |
                | Tree priors | Yule, BirthDeath, FossilizedBirthDeath, Coalescent, SkylineCoalescent, constant/exponential/logistic/compound population functions |
                | Substitution models | JC69, K80, F81, HKY, GTR, JTT, WAG, LG, Mk, GY94 |
                | Site/branch models | SiteModel, drawn site rates, strict clock, relaxed clock, drawn branch rates |
                | Observations/calibration | observed-as alignment/scalar/int handling, root-age calibration, MRCA calibration, dated-tip handling |
                | MCMC/logging/operators | chainLength, randomSeed, defaultLogEvery, outputPrefix, screen/file/tree loggers, automatic parameter/tree/simplex/clock operators |
                """;
    }

    private static String selectedModelCoverageTable() throws Exception {
        StringBuilder table =
                new StringBuilder();

        table.append("## Selected Representative Model Coverage\n\n");
        table.append("| Model | Demonstrates | Validated path | State | Priors | Tree priors | Calib | Likelihoods | Operators | MCMC |\n");
        table.append("| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");

        for (CoverageExample example : SELECTED_EXAMPLES) {
            assertTrue(
                    Files.exists(example.path),
                    "Missing selected representative example: " + example.path
            );

            BeastXModelSummary summary =
                    summaryFor(example.path);

            table.append("| ")
                    .append(modelName(example.path))
                    .append(" | ")
                    .append(example.demonstrates)
                    .append(" | ")
                    .append(example.validatedPath)
                    .append(" | ")
                    .append(summary.stateNodes.size())
                    .append(" | ")
                    .append(summary.parameterPriors.size())
                    .append(" | ")
                    .append(summary.treePriors.size())
                    .append(" | ")
                    .append(summary.calibrationPriors.size())
                    .append(" | ")
                    .append(summary.likelihoods.size())
                    .append(" | ")
                    .append(summary.operators.size())
                    .append(" | ")
                    .append(hasMCMCConfig(summary) ? "yes" : "no")
                    .append(" |\n");
        }

        return table.toString();
    }

    private static String xmlCoverageTable() {
        return """
                ## XML Coverage

                | XML area | Current tested coverage |
                | --- | --- |
                | Scalar-prior MCMC | LogNormal scalar parameter XML, parameter logger, BEAST X parse/run |
                | Tree-prior MCMC | Yule, BirthDeath, parameterized BirthDeath, constant-population Coalescent |
                | Calibration XML | Uniform root calibration and MRCA calibration |
                | PhyloCTMC XML | strict-clock treeLikelihood, fixed GTR, partitioned GTR/HKY likelihoods, shared tree/clock, site models |
                | Runner entry points | write-and-run XML, structured XML run result, options-based XML run, build XML run from `.phylospec` file |
                """;
    }

    private static String beastXXmlExportQuestionTable() {
        return """
                ## BEAST X Object-to-XML Question

                | Topic | Current finding |
                | --- | --- |
                | BEAST 2.8 comparison | BEAST 2.8 has `XMLProducer` for generating XML from BEAST objects. |
                | BEAST X runtime objects | I have not found a general `toXML()` method or object-graph serializer for common runtime objects such as `Parameter`, `TreeModel`, `Likelihood`, or `MCMC`. |
                | BEAUti route | BEAUti appears to write XML from `BeautiOptions` through `BeastGenerator` and `XMLWriter`, rather than from arbitrary runtime BEAST objects. |
                | Current PhyloSpec route | The BEAST X backend keeps the object-level execution path and also maintains a PhyloSpec-side XML plan/export layer. |
                | Question for Marc | Does BEAST X provide an existing API to serialize constructed BEAST runtime objects back into a complete XML document? |
                """;
    }

    private static BeastXModelSummary summaryFor(Path path) throws Exception {
        BeastXModel model =
                new PhyloSpecRunner(readSource(path))
                        .buildModel(modelName(path));

        return BeastXModelSummary.from(model);
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

    private static String modelName(Path path) {
        String fileName =
                path.getFileName().toString();

        return fileName.replaceFirst("\\.phylospec$", "");
    }

    private record CoverageExample(
            Path path,
            String demonstrates,
            String validatedPath
    ) {
    }
}
