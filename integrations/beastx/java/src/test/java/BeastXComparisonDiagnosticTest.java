import org.junit.jupiter.api.Test;
import tiling.runner.BeastXRunResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXComparisonDiagnosticTest {

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

        Path outputDirectory =
                Path.of("target", "comparison-diagnostics");

        Path logPath =
                outputDirectory.resolve("diagnostic-h1n1-fixed-clock-beastx.log");

        Path treeLogPath =
                outputDirectory.resolve("diagnostic-h1n1-fixed-clock-beastx.trees");

        Files.createDirectories(outputDirectory);
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1DatedFixedClockHKYGamma"
                        );

        assertNotNull(result);
        assertNotNull(result.model());
        assertNotNull(result.mcmc());
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected BEAST X diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected BEAST X diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected BEAST X diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected BEAST X diagnostic tree log to be non-empty.");

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
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-constant-coalescent-prior-only.log"
                ),
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-constant-coalescent-prior-only.trees"
                )
        );
    }

    @Test
    public void writesPriorOnlyExponentialZeroGrowthDiagnosticLogs() throws Exception {
        runPriorOnlyDiagnostic(
                "diagnosticH1N1DatedExponentialZeroGrowthPriorOnly.phylospec",
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-exponential-zero-growth-prior-only.log"
                ),
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-exponential-zero-growth-prior-only.trees"
                )
        );
    }

    @Test
    public void writesFixedPopulationExponentialCoalescentHKYGammaDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1DatedFixedPopulationExponentialCoalescentHKYGamma.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-population-exponential-coalescent-hky-gamma.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-population-exponential-coalescent-hky-gamma.trees"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1DatedFixedPopulationExponentialCoalescentHKYGamma"
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected diagnostic tree log to be non-empty.");
    }

    @Test
    public void writesFixedTreeSingleStateExponentialCoalescentHKYGammaDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1FixedTreeSingleStateExponentialCoalescentHKYGamma.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-tree-single-state-exponential-coalescent-hky-gamma.log"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1FixedTreeSingleStateExponentialCoalescentHKYGamma"
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("tree_prior"), log);
        assertTrue(log.contains("alignment_likelihood"), log);
        assertTrue(log.contains("tree.height"), log);
        assertTrue(log.contains("tree.treeLength"), log);

    }

    @Test
    public void writesFixedTreeClockRateOnlyHKYDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1FixedTreeClockRateOnlyHKY.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-tree-clockrate-only-hky.log"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1FixedTreeClockRateOnlyHKY"
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("alignment_likelihood"), log);
        assertTrue(log.contains("clockRate"), log);
    }

    @Test
    public void writesFixedTreeFixedGammaClockRateHKYDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1FixedTreeFixedGammaClockRateHKY.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-tree-fixed-gamma-clockrate-hky.log"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1FixedTreeFixedGammaClockRateHKY"
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("alignment_likelihood"), log);
        assertTrue(log.contains("clockRate"), log);
    }

    private static void runPriorOnlyDiagnostic(
            String sourceFileName,
            Path logPath,
            Path treeLogPath
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

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                sourceFileName.replace(".phylospec", "")
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected diagnostic tree log to be non-empty.");
    }

    @Test
    public void writesFixedSubstitutionFixedClockExponentialCoalescentDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1DatedFixedSubstitutionFixedClockExponentialCoalescent.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-substitution-fixed-clock-exponential-coalescent.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-fixed-substitution-fixed-clock-exponential-coalescent.trees"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1DatedFixedSubstitutionFixedClockExponentialCoalescent"
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected diagnostic tree log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("tree.height"), log);
        assertTrue(log.contains("tree.treeLength"), log);
        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
    }

    @Test
    public void writesStochasticClockFixedSubstitutionExponentialCoalescentDiagnosticLogs() throws Exception {
        Path sourcePath =
                Path.of(
                        "src",
                        "test",
                        "java",
                        "resources",
                        "comparison",
                        "legacy",
                        "diagnosticH1N1DatedStochasticClockFixedSubstitutionExponentialCoalescent.phylospec"
                );

        Path logPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-stochastic-clock-fixed-substitution-exponential-coalescent.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "beastx-stochastic-clock-fixed-substitution-exponential-coalescent.trees"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "diagnosticH1N1DatedStochasticClockFixedSubstitutionExponentialCoalescent"
                        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected diagnostic log file.");
        assertTrue(Files.size(logPath) > 0, "Expected diagnostic log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected diagnostic tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected diagnostic tree log to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("tree.height"), log);
        assertTrue(log.contains("tree.treeLength"), log);
        assertTrue(log.contains("populationSize"), log);
        assertTrue(log.contains("growthRate"), log);
        assertTrue(log.contains("clockRate"), log);
    }

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
                        "phylo-beastx-phase0-fixed-tree-jc69-estimated-clock.log"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase0-fixed-tree-jc69-estimated-clock.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase0-fixed-tree-jc69-estimated-clock.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        long startNanos =
                System.nanoTime();

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "phase0FixedTreeJc69EstimatedClock"
                        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.write(
                operatorSummaryPath,
                result.operatorSummaries()
        );

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected phase 0 BEAST X log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 0 BEAST X log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 0 BEAST X operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 0 BEAST X operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 0 BEAST X runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 0 BEAST X runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("alignment_likelihood"), log);
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
                        "beastx-phase0-fixed-tree-jc69-estimated-clock.xml"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourcePath));

        runner.writeXml(
                "phase0FixedTreeJc69EstimatedClock",
                xmlPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 0 BEAST X XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 0 BEAST X XML file to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("clockRate"), xml);
        assertTrue(xml.contains("joint"), xml);
        assertTrue(xml.contains("prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("beastx-phase0-fixed-tree-jc69-estimated-clock.log"), xml);
        assertFalse(xml.contains("phylo-beastx-phase0-fixed-tree-jc69-estimated-clock.log"), xml);
    }

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
                        "phylo-beastx-phase1-hky-estimated-tree.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase1-hky-estimated-tree.trees"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase1-hky-estimated-tree.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase1-hky-estimated-tree.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        long startNanos =
                System.nanoTime();

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "phase1HkyEstimatedTree"
                        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.write(
                operatorSummaryPath,
                result.operatorSummaries()
        );

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected phase 1 BEAST X log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 1 BEAST X log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected phase 1 BEAST X tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected phase 1 BEAST X tree log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 1 BEAST X operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 1 BEAST X operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 1 BEAST X runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 1 BEAST X runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("alignment_likelihood"), log);
        assertTrue(log.contains("tree_prior"), log);
        assertTrue(log.contains("kappa"), log);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("STATE_0"), treeLog);
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
                        "beastx-phase1-hky-estimated-tree.xml"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourcePath));

        runner.writeXml(
                "phase1HkyEstimatedTree",
                xmlPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 1 BEAST X XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 1 BEAST X XML file to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("kappa"), xml);
        assertTrue(xml.contains("baseFrequencies"), xml);
        assertTrue(xml.contains("baseFrequencies_prior"), xml);
        assertTrue(xml.contains("tree_prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("beastx-phase1-hky-estimated-tree.log"), xml);
        assertTrue(xml.contains("beastx-phase1-hky-estimated-tree.trees"), xml);
        assertFalse(xml.contains("phylo-beastx-phase1-hky-estimated-tree.log"), xml);
        assertFalse(xml.contains("phylo-beastx-phase1-hky-estimated-tree.trees"), xml);
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
                        "phylo-beastx-phase2-hky-gamma-estimated-tree.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase2-hky-gamma-estimated-tree.trees"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase2-hky-gamma-estimated-tree.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase2-hky-gamma-estimated-tree.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        long startNanos =
                System.nanoTime();

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "phase2HkyGammaEstimatedTree"
                        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.write(
                operatorSummaryPath,
                result.operatorSummaries()
        );

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected phase 2 BEAST X log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 2 BEAST X log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected phase 2 BEAST X tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected phase 2 BEAST X tree log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 2 BEAST X operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 2 BEAST X operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 2 BEAST X runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 2 BEAST X runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("alignment_likelihood"), log);
        assertTrue(log.contains("tree_prior"), log);
        assertTrue(log.contains("kappa"), log);
        assertTrue(log.contains("baseFrequencies"), log);
        assertTrue(log.contains("gammaShape"), log);

        assertTrue(treeLog.contains("#NEXUS"), treeLog);
        assertTrue(treeLog.contains("STATE_0"), treeLog);
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
                        "beastx-phase2-hky-gamma-estimated-tree.xml"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourcePath));

        runner.writeXml(
                "phase2HkyGammaEstimatedTree",
                xmlPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 2 BEAST X XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 2 BEAST X XML file to be non-empty.");

        String xml =
                Files.readString(xmlPath);

        assertTrue(xml.contains("kappa"), xml);
        assertTrue(xml.contains("baseFrequencies"), xml);
        assertTrue(xml.contains("baseFrequencies_prior"), xml);
        assertTrue(xml.contains("gammaShape"), xml);
        assertTrue(xml.contains("gammaShape_prior"), xml);
        assertTrue(xml.contains("tree_prior"), xml);
        assertTrue(xml.contains("likelihood"), xml);
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("beastx-phase2-hky-gamma-estimated-tree.log"), xml);
        assertTrue(xml.contains("beastx-phase2-hky-gamma-estimated-tree.trees"), xml);
        assertFalse(xml.contains("phylo-beastx-phase2-hky-gamma-estimated-tree.log"), xml);
        assertFalse(xml.contains("phylo-beastx-phase2-hky-gamma-estimated-tree.trees"), xml);
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
                        "phylo-beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.log"
                );

        Path treeLogPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.trees"
                );

        Path operatorSummaryPath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.operators.txt"
                );

        Path runtimePath =
                Path.of(
                        "target",
                        "comparison-diagnostics",
                        "phylo-beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.runtime.txt"
                );

        Files.createDirectories(logPath.getParent());
        Files.deleteIfExists(logPath);
        Files.deleteIfExists(treeLogPath);
        Files.deleteIfExists(operatorSummaryPath);
        Files.deleteIfExists(runtimePath);

        long startNanos =
                System.nanoTime();

        BeastXRunResult result =
                new PhyloSpecRunner(Files.readString(sourcePath))
                        .executeMaterialized(
                                "phase4H1N1RegionPartitionedHkyGammaExponentialCoalescent"
                        );

        long elapsedNanos =
                System.nanoTime() - startNanos;

        Files.write(
                operatorSummaryPath,
                result.operatorSummaries()
        );

        Files.writeString(
                runtimePath,
                "elapsed_seconds=" + (elapsedNanos / 1_000_000_000.0) + System.lineSeparator()
        );

        assertNotNull(result);
        assertTrue(result.executed());

        assertTrue(Files.exists(logPath), "Expected phase 4 BEAST X log file.");
        assertTrue(Files.size(logPath) > 0, "Expected phase 4 BEAST X log to be non-empty.");

        assertTrue(Files.exists(treeLogPath), "Expected phase 4 BEAST X tree log file.");
        assertTrue(Files.size(treeLogPath) > 0, "Expected phase 4 BEAST X tree log to be non-empty.");

        assertTrue(Files.exists(operatorSummaryPath), "Expected phase 4 BEAST X operator summary.");
        assertTrue(Files.size(operatorSummaryPath) > 0, "Expected phase 4 BEAST X operator summary to be non-empty.");

        assertTrue(Files.exists(runtimePath), "Expected phase 4 BEAST X runtime file.");
        assertTrue(Files.size(runtimePath) > 0, "Expected phase 4 BEAST X runtime file to be non-empty.");

        String log =
                Files.readString(logPath);

        String treeLog =
                Files.readString(treeLogPath);

        assertTrue(log.contains("posterior"), log);
        assertTrue(log.contains("prior"), log);
        assertTrue(log.contains("likelihood"), log);
        assertTrue(log.contains("tree_prior"), log);
        assertTrue(log.contains("firstRegionAlignment_likelihood"), log);
        assertTrue(log.contains("secondRegionAlignment_likelihood"), log);
        assertTrue(log.contains("thirdRegionAlignment_likelihood"), log);
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
        assertTrue(treeLog.contains("STATE_0"), treeLog);
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
                        "beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.xml"
                );

        Files.createDirectories(xmlPath.getParent());
        Files.deleteIfExists(xmlPath);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(Files.readString(sourcePath));

        runner.writeXml(
                "phase4H1N1RegionPartitionedHkyGammaExponentialCoalescent",
                xmlPath
        );

        assertTrue(Files.exists(xmlPath), "Expected phase 4 BEAST X XML file.");
        assertTrue(Files.size(xmlPath) > 0, "Expected phase 4 BEAST X XML file to be non-empty.");

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
        assertTrue(xml.contains("<treeLikelihood"), xml);
        assertTrue(xml.contains("beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.log"), xml);
        assertTrue(xml.contains("beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.trees"), xml);
        assertFalse(xml.contains("phylo-beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.log"), xml);
        assertFalse(xml.contains("phylo-beastx-phase4-h1n1-region-partitioned-hky-gamma-exponential-coalescent.trees"), xml);
    }
}
