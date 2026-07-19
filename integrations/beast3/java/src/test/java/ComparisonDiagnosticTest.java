import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ComparisonDiagnosticTest {

    @Test
    public void writesSingleStepDatedH1N1FixedClockDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1DatedFixedClockHKYGamma.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "diagnostic-h1n1-fixed-clock-beast3.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "diagnostic-h1n1-fixed-clock-beast3.trees"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.runPhyloSpec(
                "target/comparison-diagnostics/diagnostic-h1n1-fixed-clock-beast3"
        );

        assertTrue(Files.exists(logPath), "Expected BEAST 3 diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST 3 diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST 3 diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST 3 diagnostic tree log to be non-empty.");

        String log =
                Files.readString(logPath);


        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("STATE_0"), treeLog);
    }

    @Test
    public void writesPriorOnlyConstantCoalescentDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1DatedConstantCoalescentPriorOnly.phylospec",
                "target/comparison-diagnostics/beast3-constant-coalescent-prior-only"
        );
    }

    @Test
    public void writesPriorOnlyExponentialZeroGrowthDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1DatedExponentialZeroGrowthPriorOnly.phylospec",
                "target/comparison-diagnostics/beast3-exponential-zero-growth-prior-only"
        );
    }

    @Test
    public void writesFixedPopulationExponentialCoalescentHKYGammaDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1DatedFixedPopulationExponentialCoalescentHKYGamma.phylospec",
                "target/comparison-diagnostics/beast3-fixed-population-exponential-coalescent-hky-gamma"
        );
    }

    @Test
    public void writesFixedTreeSingleStateExponentialCoalescentHKYGammaDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1FixedTreeSingleStateExponentialCoalescentHKYGamma.phylospec",
                "target/comparison-diagnostics/beast3-fixed-tree-single-state-exponential-coalescent-hky-gamma"
        );
    }

    @Test
    public void writesFixedTreeClockRateOnlyHKYDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1FixedTreeClockRateOnlyHKY.phylospec",
                "target/comparison-diagnostics/beast3-fixed-tree-clockrate-only-hky"
        );
    }

    @Test
    public void writesFixedTreeFixedGammaClockRateHKYDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1FixedTreeFixedGammaClockRateHKY.phylospec",
                "target/comparison-diagnostics/beast3-fixed-tree-fixed-gamma-clockrate-hky"
        );
    }

    private static void runPriorOnlyDiagnostic(
            String sourceFileName,
            String outputPrefix
    ) throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        sourceFileName
                );

        Path logPath =
                Path.of(outputPrefix + ".log");

        Path treeLogPath =
                Path.of(outputPrefix + ".trees");

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.runPhyloSpec(outputPrefix);

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected diagnostic tree log to be non-empty.");
    }

    @Test
    public void writesFixedSubstitutionFixedClockExponentialCoalescentDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1DatedFixedSubstitutionFixedClockExponentialCoalescent.phylospec",
                "target/comparison-diagnostics/beast3-fixed-substitution-fixed-clock-exponential-coalescent"
        );
    }

    @Test
    public void writesStochasticClockFixedSubstitutionExponentialCoalescentDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1DatedStochasticClockFixedSubstitutionExponentialCoalescent.phylospec",
                "target/comparison-diagnostics/beast3-stochastic-clock-fixed-substitution-exponential-coalescent"
        );
    }

    // Phase 0
    @Test
    public void writesPhase0FixedTreeJc69EstimatedClockDiagnosticLog() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase0",
                        "phase0FixedTreeJc69EstimatedClock.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase0-fixed-tree-jc69-estimated-clock.log"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase0-fixed-tree-jc69-estimated-clock.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase0-fixed-tree-jc69-estimated-clock.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        long startNanos =
                System.nanoTime();

        runner.runPhyloSpec(
                "target/comparison-diagnostics/phylo-beast3-phase0-fixed-tree-jc69-estimated-clock",
                operatorSummaryPath
        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertTrue(Files.exists(logPath), "Expected phase 0 BEAST 3 log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 0 BEAST 3 log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 0 BEAST 3 operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 0 BEAST 3 operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 0 BEAST 3 runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 0 BEAST 3 runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("clockRate"), log);
    }

    @Test
    public void writesPhase0FixedTreeJc69EstimatedClockXml() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase0",
                        "phase0FixedTreeJc69EstimatedClock.phylospec"
                );

        Path xmlPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase0-fixed-tree-jc69-estimated-clock.xml"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase0-fixed-tree-jc69-estimated-clock.xml.operators.txt"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.writeXml(
                "target/comparison-diagnostics/beast3-phase0-fixed-tree-jc69-estimated-clock",
                xmlPath,
                operatorSummaryPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 0 BEAST 3 XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 0 BEAST 3 XML file to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 0 BEAST 3 XML operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 0 BEAST 3 XML operator summary to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("clockRate"), xml);
        assertTrue(xml.contains("posterior"), xml);
        assertTrue(xml.contains("prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("beast3-phase0-fixed-tree-jc69-estimated-clock.log"), xml);
        assertFalse(xml.contains("phylo-beast3-phase0-fixed-tree-jc69-estimated-clock.log"), xml);
    }

    // Phase 1
    @Test
    public void writesPhase1HkyEstimatedTreeDiagnosticLog() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase1",
                        "phase1HkyEstimatedTree.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase1-hky-estimated-tree.log"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase1-hky-estimated-tree.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase1-hky-estimated-tree.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        long startNanos =
                System.nanoTime();

        runner.runPhyloSpec(
                "target/comparison-diagnostics/phylo-beast3-phase1-hky-estimated-tree",
                operatorSummaryPath
        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertTrue(Files.exists(logPath), "Expected phase 1 BEAST 3 log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 1 BEAST 3 log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 1 BEAST 3 operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 1 BEAST 3 operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 1 BEAST 3 runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 1 BEAST 3 runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("tree_prior"), log);
        assertTrue(log.contains("baseFrequencies"), log);
    }

    @Test
    public void writesPhase1HkyEstimatedTreeXml() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase1",
                        "phase1HkyEstimatedTree.phylospec"
                );

        Path xmlPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase1-hky-estimated-tree.xml"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase1-hky-estimated-tree.xml.operators.txt"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.writeXml(
                "target/comparison-diagnostics/beast3-phase1-hky-estimated-tree",
                xmlPath,
                operatorSummaryPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 1 BEAST 3 XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 1 BEAST 3 XML file to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 1 BEAST 3 XML operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 1 BEAST 3 XML operator summary to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("kappa"), xml);
        assertTrue(xml.contains("baseFrequencies"), xml);
        assertTrue(xml.contains("baseFrequencies_prior"), xml);
        assertTrue(xml.contains("tree_prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("beast3-phase1-hky-estimated-tree.log"), xml);
        assertFalse(xml.contains("phylo-beast3-phase1-hky-estimated-tree.log"), xml);
    }

    @Test
    public void writesPhase2HkyGammaEstimatedTreeDiagnosticLog() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase2",
                        "phase2HkyGammaEstimatedTree.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase2-hky-gamma-estimated-tree.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase2-hky-gamma-estimated-tree.trees"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase2-hky-gamma-estimated-tree.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase2-hky-gamma-estimated-tree.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        long startNanos =
                System.nanoTime();

        runner.runPhyloSpec(
                "target/comparison-diagnostics/phylo-beast3-phase2-hky-gamma-estimated-tree",
                operatorSummaryPath
        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertTrue(Files.exists(logPath), "Expected phase 2 BEAST 3 log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 2 BEAST 3 log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected phase 2 BEAST 3 tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected phase 2 BEAST 3 tree log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 2 BEAST 3 operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 2 BEAST 3 operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 2 BEAST 3 runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 2 BEAST 3 runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("baseFrequencies"), log);
        assertTrue(log.contains("gammaShape"), log);
        assertTrue(log.contains("tree_prior"), log);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("tree STATE_"), treeLog);
    }

    @Test
    public void writesPhase2HkyGammaEstimatedTreeXml() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase2",
                        "phase2HkyGammaEstimatedTree.phylospec"
                );

        Path xmlPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase2-hky-gamma-estimated-tree.xml"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase2-hky-gamma-estimated-tree.xml.operators.txt"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.writeXml(
                "target/comparison-diagnostics/beast3-phase2-hky-gamma-estimated-tree",
                xmlPath,
                operatorSummaryPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 2 BEAST 3 XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 2 BEAST 3 XML file to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 2 BEAST 3 XML operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 2 BEAST 3 XML operator summary to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("kappa"), xml);
        assertTrue(xml.contains("baseFrequencies"), xml);
        assertTrue(xml.contains("baseFrequencies_prior"), xml);
        assertTrue(xml.contains("gammaShape"), xml);
        assertTrue(xml.contains("gammaShape_prior"), xml);
        assertTrue(xml.contains("tree_prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("beast3-phase2-hky-gamma-estimated-tree.log"), xml);
        assertFalse(xml.contains("phylo-beast3-phase2-hky-gamma-estimated-tree.log"), xml);
    }

    @Test
    public void writesPhase4H1N1RegionPartitionedHkyGammaExponentialCoalescentDiagnosticLog() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase4",
                        "phase4H1N1RegionPartitionedHkyGammaExponentialCoalescent.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.trees"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        long startNanos =
                System.nanoTime();

        runner.runPhyloSpec(
                "target/comparison-diagnostics/phylo-beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent",
                operatorSummaryPath
        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertTrue(Files.exists(logPath), "Expected phase 4 BEAST 3 log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 4 BEAST 3 log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected phase 4 BEAST 3 tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected phase 4 BEAST 3 tree log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 4 BEAST 3 operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 4 BEAST 3 operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 4 BEAST 3 runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 4 BEAST 3 runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("tree_prior"), log);
        assertTrue(log.contains("clockRate"), log);
        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("firstKappa"), log);
        assertTrue(log.contains("secondKappa"), log);
        assertTrue(log.contains("thirdKappa"), log);
        assertTrue(log.contains("firstGammaShape"), log);
        assertTrue(log.contains("secondGammaShape"), log);
        assertTrue(log.contains("thirdGammaShape"), log);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("tree STATE_"), treeLog);
    }

    @Test
    public void writesPhase4H1N1RegionPartitionedHkyGammaExponentialCoalescentXml() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "phases",
                        "phase4",
                        "phase4H1N1RegionPartitionedHkyGammaExponentialCoalescent.phylospec"
                );

        Path xmlPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.xml"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.xml.operators.txt"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);
        Files.deleteIfExists(operatorSummaryPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(
                        Files.readString(sourcePath)
                );

        runner.writeXml(
                "target/comparison-diagnostics/beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent",
                xmlPath,
                operatorSummaryPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 4 BEAST 3 XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 4 BEAST 3 XML file to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 4 BEAST 3 XML operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 4 BEAST 3 XML operator summary to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("clockRate"), xml);
        assertTrue(xml.contains("populationSize"), xml);
        assertTrue(xml.contains("growthRate"), xml);
        assertTrue(xml.contains("firstKappa"), xml);
        assertTrue(xml.contains("secondKappa"), xml);
        assertTrue(xml.contains("thirdKappa"), xml);
        assertTrue(xml.contains("firstGammaShape"), xml);
        assertTrue(xml.contains("secondGammaShape"), xml);
        assertTrue(xml.contains("thirdGammaShape"), xml);
        assertTrue(xml.contains("tree_prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.log"), xml);
        assertFalse(xml.contains("phylo-beast3-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.log"), xml);
    }
}
