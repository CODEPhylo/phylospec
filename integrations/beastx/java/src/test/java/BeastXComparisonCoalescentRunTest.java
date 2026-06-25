import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import tiling.runner.BeastXRunResult;
import tiling.runner.XmlRunResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                                2000000
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

        XmlRunResult result =
                PhyloSpecRunner.buildXmlRunFromFile(
                        sourcePath,
                        xmlPath
                );

        assertNotNull(result);
        assertNotNull(result.model());
        assertNotNull(result.mcmc());

        assertTrue(Files.exists(xmlPath), "Expected BEAST X XML file to be written.");
        assertTrue(Files.size(xmlPath) > 0, "Expected BEAST X XML file to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("clockRate"), xml);
        assertTrue(xml.contains("clockRate_prior"), xml);
        assertTrue(xml.contains("joint"), xml);
        assertTrue(xml.contains("prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
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
