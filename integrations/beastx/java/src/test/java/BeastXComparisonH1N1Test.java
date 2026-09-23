import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tiling.runner.BeastXRunResult;
import tiling.runner.RunMode;
import tiling.runner.RunnerOptions;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXComparisonH1N1Test {

    private static final Path SOURCE_PATH =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "resources",
                    "comparison",
                    "tutorialH1N1DatedExponentialCoalescentHKYGamma.phylospec"
            );

    private static final Path OUTPUT_DIRECTORY =
            Path.of("target", "comparison");

    private static final String RUN_NAME =
            "phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma";

    private static final String XML_RUN_NAME =
            "xml-phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma";

    @Test
    public void writesTutorialH1N1DatedExponentialCoalescentHKYGammaXml() throws Exception {
        Path xmlPath =
                OUTPUT_DIRECTORY.resolve(XML_RUN_NAME + ".xml");

        Files.createDirectories(OUTPUT_DIRECTORY);
        Files.deleteIfExists(xmlPath);

        String source =
                sourceWithXmlFriendlyLogFileNames();

        String xml =
                XmlTestSupport.writeXml(
                        new PhyloSpecRunner(source)
                                .buildModel(XML_RUN_NAME),
                        xmlPath
                );

        XmlTestSupport.assertXmlContains(xml, "<coalescentSimulator id=\"tree_startingTree\"");
        XmlTestSupport.assertXmlContains(xml, "<exponentialGrowth idref=\"tree_prior_model\"/>");
        XmlTestSupport.assertXmlContains(xml, "<treeModel id=\"tree\">");
        XmlTestSupport.assertXmlContains(xml, "<coalescentSimulator idref=\"tree_startingTree\"/>");

        XmlTestSupport.assertXmlContains(xml, "<treeHeightStatistic id=\"tree.height\"");
        XmlTestSupport.assertXmlContains(xml, "<treeLengthStatistic id=\"tree.treeLength\"");
        XmlTestSupport.assertXmlContains(xml, "<treeHeightStatistic idref=\"tree.height\"/>");
        XmlTestSupport.assertXmlContains(xml, "<treeLengthStatistic idref=\"tree.treeLength\"/>");

        XmlTestSupport.assertXmlContains(xml, "<frequencyModel");
        XmlTestSupport.assertXmlContains(xml, "<dirichletParameterPrior id=\"baseFrequencies_prior\"");
        XmlTestSupport.assertXmlContains(xml, "<gammaShape gammaCategories=\"4\"");
        XmlTestSupport.assertXmlContains(
                xml,
                "fileName=\"" + XML_RUN_NAME + ".log\""
        );
        XmlTestSupport.assertXmlContains(
                xml,
                "fileName=\"" + XML_RUN_NAME + ".trees\""
        );
        XmlTestSupport.assertXmlDoesNotContain(xml, "fileName=\"target/comparison/");

        assertTrue(Files.exists(xmlPath), "Expected comparison XML to be written.");
        assertTrue(Files.size(xmlPath) > 0, "Expected comparison XML to be non-empty.");
    }

    @Test
    @Tag("beagle")
    public void runsTutorialH1N1DatedExponentialCoalescentHKYGammaModelAndWritesLogs() throws Exception {
        Path logPath =
                OUTPUT_DIRECTORY.resolve(RUN_NAME + ".log");

        Path treeLogPath =
                OUTPUT_DIRECTORY.resolve(RUN_NAME + ".trees");

        Files.createDirectories(OUTPUT_DIRECTORY);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                Files.readString(SOURCE_PATH)
                        .replace(
                                "target/comparison/phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.log",
                                unixPath(logPath)
                        )
                        .replace(
                                "target/comparison/phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.trees",
                                unixPath(treeLogPath)
                        );

        BeastXRunResult result;

        try {
            result =
                    new PhyloSpecRunner(source)
                            .run(
                                    RunnerOptions.builder(RUN_NAME)
                                            .mode(RunMode.EXECUTE_MCMC)
                                            .materializePhyloCTMC(true)
                                            .chainLengthOverride(10)
                                            .build()
                            );
        } catch (RuntimeException exception) {
            Assumptions.assumeFalse(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    "Skipping H1N1 comparison direct run because BEAGLE native library is not available."
            );

            throw exception;
        }

        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(result.executed());

        XmlTestSupport.assertNonEmptyFile(logPath, "H1N1 comparison parameter log");
        XmlTestSupport.assertNonEmptyFile(treeLogPath, "H1N1 comparison tree log");
    }

    private static String unixPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    private static String sourceWithXmlFriendlyLogFileNames() throws Exception {
        return Files.readString(SOURCE_PATH)
                .replace(
                        "target/comparison/phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.log",
                        XML_RUN_NAME + ".log"
                )
                .replace(
                        "target/comparison/phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.trees",
                        XML_RUN_NAME + ".trees"
                );
    }
}
