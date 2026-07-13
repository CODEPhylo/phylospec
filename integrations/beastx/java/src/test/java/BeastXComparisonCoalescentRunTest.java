import java.nio.file.Files;
import java.nio.file.Path;

import dr.inference.mcmc.MCMC;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import tiling.runner.BeastXRunResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration-test")
public class BeastXComparisonCoalescentRunTest {

    @Test
    public void runsTutorialExponentialCoalescentHKYGammaModelAndWritesLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "exponentialCoalescentHKYGamma.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path logPath =
                outputDirectory.resolve("exponential-coalescent-hky-gamma-beastx.log");

        Path treeLogPath =
                outputDirectory.resolve("exponential-coalescent-hky-gamma-beastx.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        String source =
                Files.readString(sourcePath);

        BeastXRunResult result =
                new PhyloSpecRunner(source)
                        .executeMaterialized(
                                "tutorialExponentialCoalescentHKYGamma",
                                1000000
                        );

        assertNotNull(result);
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected BEAST X log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST X log file to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X tree log file to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X tree log file to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("clockRate"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("baseFrequencies"), log);
        assertTrue(log.contains("gammaShape"), log);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void runsTutorialH1N1ExponentialCoalescentHKYGammaModelAndWritesLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "tutorialH1N1ExponentialCoalescentHKYGamma.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path logPath =
                outputDirectory.resolve("tutorial-h1n1-exponential-coalescent-hky-gamma-beastx.log");

        Path treeLogPath =
                outputDirectory.resolve("tutorial-h1n1-exponential-coalescent-hky-gamma-beastx.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "tutorialH1N1ExponentialCoalescentHKYGamma",
                                10000000
                        );

        assertNotNull(result);
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected BEAST X H1N1 log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST X H1N1 log file to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X H1N1 tree log file to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X H1N1 tree log file to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("clockRate"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("baseFrequencies"), log);
        assertTrue(log.contains("gammaShape"), log);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void runsTutorialH1N1DatedExponentialCoalescentHKYGammaModelAndWritesLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "tutorialH1N1DatedExponentialCoalescentHKYGamma.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path logPath =
                outputDirectory.resolve(
                        "phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.log"
                );

        Path treeLogPath =
                outputDirectory.resolve(
                        "phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.trees"
                );

        Path operatorSummaryPath =
                outputDirectory.resolve(
                        "phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.operators.txt"
                );

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "tutorialH1N1DatedExponentialCoalescentHKYGamma"
                        );

        assertNotNull(result);
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(result.executed());

        Files.write(operatorSummaryPath, result.operatorSummaries());

        assertTrue(Files.exists(logPath), "Expected BEAST X dated H1N1 log file to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST X dated H1N1 log file to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X dated H1N1 tree log file to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X dated H1N1 tree log file to be non-empty.");

        String log =
                Files.readString(logPath);


        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("Begin trees;"), treeLog);
        assertTrue(treeLog.contains("STATE_"), treeLog);
    }

    @Test
    public void writesTutorialH1N1DatedExponentialCoalescentHKYGammaXml() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "tutorialH1N1DatedExponentialCoalescentHKYGamma.phylospec"
                );

        Path outputDirectory =
                Path.of("target", "comparison");

        Path xmlPath =
                outputDirectory.resolve(
                        "phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.xml"
                );

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(xmlPath);

        PhyloSpecRunner runner =
                PhyloSpecRunner.fromFile(sourcePath);

        runner.writeXml(
                "tutorialH1N1DatedExponentialCoalescentHKYGamma",
                xmlPath
        );

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");
        assertTrue(Files.size(xmlPath) > 0, "Expected BEAST X XML file to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("clockRate"), xml);
        assertTrue(xml.contains("clockRate_prior"), xml);
        assertTrue(xml.contains("joint"), xml);
        assertTrue(xml.contains("prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("fileName=\"beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.log\""), xml);
        assertTrue(xml.contains("fileName=\"beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.trees\""), xml);
        assertFalse(xml.contains("fileName=\"target/comparison/"), xml);
        assertFalse(xml.contains("fileName=\"phylo-beastx-tutorial-h1n1-dated-exponential-coalescent-hky-gamma"), xml);

        assertTrue(xml.contains("<coalescentSimulator id=\"tree_startingTree\">"), xml);
        assertTrue(xml.contains("<taxa id=\"tree_startingTaxa\">"), xml);
        assertTrue(xml.contains("<exponentialGrowth idref=\"tree_prior_model\"/>"), xml);
        assertTrue(xml.contains("<coalescentSimulator idref=\"tree_startingTree\"/>"), xml);
        assertFalse(xml.contains("<newick id=\"tree_startingTree\""), xml);

        assertTrue(xml.contains("<scaleOperator id=\"clockRate_scale\" scaleFactor=\"0.75\" weight=\"1.0\">"), xml);
        assertTrue(xml.contains("<scaleOperator id=\"populationSize_scale\" scaleFactor=\"0.75\" weight=\"1.0\">"), xml);
        assertTrue(xml.contains("<deltaExchange id=\"baseFrequencies_deltaExchange\" delta=\"0.01\" weight=\"1.0\">"), xml);

        assertFalse(xml.contains("<nodeHeightScaleOperator"), xml);
        assertTrue(xml.contains("<nodeHeightOperator id=\"tree_uniformNodeHeight\" weight=\"30.0\">"), xml);
        assertFalse(xml.contains("<nodeHeightOperator id=\"tree_randomWalkNodeHeight\""), xml);
        assertTrue(xml.contains("<narrowExchange id=\"tree_narrowExchange\" weight=\"15.0\">"), xml);
        assertTrue(xml.contains("<wideExchange id=\"tree_wideExchange\" weight=\"5.0\">"), xml);
        assertTrue(xml.contains("<subtreeSlide id=\"tree_subtreeSlide\" weight=\"15.0\" size=\"15.0\" gaussian=\"true\">"), xml);
        assertTrue(xml.contains("<wilsonBalding id=\"tree_wilsonBalding\" weight=\"5.0\">"), xml);
        assertTrue(xml.contains("<upDownOperator id=\"tree_clockRate_upDown\" scaleFactor=\"0.75\" weight=\"5.0\">"), xml);

        try {
            MCMC mcmc =
                    runner.parseXmlMCMC(xmlPath);

            assertNotNull(mcmc);
        } catch (RuntimeException exception) {
            assertTrue(
                    XmlTestSupport.isMissingBeagleLibrary(exception),
                    exception.toString()
            );
        }
    }

    @Test
    public void runsTutorialH1N1DatedExponentialCoalescentHKYGammaFixedClockModelAndWritesLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "tutorialH1N1DatedExponentialCoalescentHKYGammaFixedClock.phylospec"
                );

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "tutorialH1N1DatedExponentialCoalescentHKYGammaFixedClock",
                                2000000
                        );

        assertNotNull(result);
        assertTrue(result.executed());
    }
}
