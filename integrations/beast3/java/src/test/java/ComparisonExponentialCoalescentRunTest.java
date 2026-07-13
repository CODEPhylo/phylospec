import java.nio.file.Files;
import java.nio.file.Path;

import beast.base.inference.MCMC;
import beast.base.inference.Runnable;
import beast.base.parser.XMLParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComparisonExponentialCoalescentRunTest {

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

        Path logPath =
                Path.of(
                        "target",
                        "comparison",
                        "exponential-coalescent-hky-gamma-beast3.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison",
                        "exponential-coalescent-hky-gamma-beast3.trees"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.runPhyloSpec("target/comparison/exponential-coalescent-hky-gamma-beast3");

        assertTrue(Files.exists(logPath), "Expected BEAST 3 comparison log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST 3 comparison log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST 3 comparison tree log to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST 3 comparison tree log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("clockRate"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("gammaShape"), log);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("tree STATE_"), treeLog);
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

        Path logPath =
                Path.of(
                        "target",
                        "comparison",
                        "tutorial-h1n1-exponential-coalescent-hky-gamma-beast3.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison",
                        "tutorial-h1n1-exponential-coalescent-hky-gamma-beast3.trees"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.runPhyloSpec("target/comparison/tutorial-h1n1-exponential-coalescent-hky-gamma-beast3");

        assertTrue(Files.exists(logPath), "Expected BEAST 3 H1N1 comparison log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST 3 H1N1 comparison log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST 3 H1N1 comparison tree log to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST 3 H1N1 comparison tree log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("clockRate"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("gammaShape"), log);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("tree STATE_"), treeLog);
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

        Path logPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.trees"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.operators.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.runPhyloSpec(
                "target/comparison/phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma",
                operatorSummaryPath
        );

        assertTrue(Files.exists(logPath), "Expected BEAST 3 dated H1N1 comparison log to be written.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST 3 dated H1N1 comparison log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST 3 dated H1N1 comparison tree log to be written.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST 3 dated H1N1 comparison tree log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected BEAST 3 operator summary to be written.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected BEAST 3 operator summary to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("kappa"), log);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("tree STATE_"), treeLog);
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

        Path xmlPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.xml"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.xml.operators.txt"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.writeXml(
                "target/comparison/beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma",
                xmlPath,
                operatorSummaryPath
        );

        assertTrue(Files.exists(xmlPath), "Expected BEAST 3 XML file to be written.");
        assertTrue(Files.size(xmlPath) > 0, "Expected BEAST 3 XML file to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected BEAST 3 XML operator summary to be written.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected BEAST 3 XML operator summary to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("clockRate"), xml);
        assertTrue(xml.contains("posterior"), xml);
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
                        "legacy",
                        "tutorialH1N1DatedExponentialCoalescentHKYGammaFixedClock.phylospec"
                );

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.runPhyloSpec(
                "target/comparison/tutorial-h1n1-dated-exponential-coalescent-hky-gamma-fixed-clock-beast3"
        );
    }

    @Test
    public void writesParsesTutorialH1N1DatedExponentialCoalescentHKYGammaXml() throws Exception {
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

        Path xmlPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.xml"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison",
                        "phylo-beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma.xml.operators.txt"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        String source =
                Files.readString(sourcePath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        runner.writeXml(
                "beast3-tutorial-h1n1-dated-exponential-coalescent-hky-gamma",
                xmlPath,
                operatorSummaryPath
        );

        assertTrue(Files.exists(xmlPath), "Expected BEAST 3 XML file to be written.");
        assertTrue(Files.size(xmlPath) > 0, "Expected BEAST 3 XML file to be non-empty.");

        Runnable runnable =
                new XMLParser()
                        .parseFile(xmlPath.toFile());

        assertTrue(
                runnable instanceof MCMC,
                "Expected generated BEAST 3 XML to parse back into an MCMC object."
        );
    }
}
