import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
