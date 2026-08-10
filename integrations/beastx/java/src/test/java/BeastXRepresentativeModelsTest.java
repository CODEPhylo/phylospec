import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.summary.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXRepresentativeModelsTest {

    private static final Path REPRESENTATIVE_MODEL_DIR =
            Path.of("src", "test", "java", "tiling", "representative");

    private static final Path SHOWCASE_MODEL_DIR =
            REPRESENTATIVE_MODEL_DIR.resolve("showcase");

    @Test
    public void representativeModelsBuildBeastXModelSummaries() throws Exception {
        List<Path> modelPaths =
                representativeModelPaths();

        assertTrue(
                modelPaths.size() >= 6,
                "Expected at least 6 representative BeastX models."
        );

        for (Path modelPath : modelPaths) {
            BeastXModel model =
                    buildModelFromFile(modelPath);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            assertTrue(
                    hasPhylogeneticContent(summary),
                    "Representative model should produce at least one phylogenetic component: " + modelPath
            );

            assertNoBlankValues(summary.stateNodes, "state nodes", modelPath);
            assertNoBlankValues(summary.parameterPriors, "parameter priors", modelPath);
            assertNoBlankValues(summary.treeModels, "tree models", modelPath);
            assertNoBlankValues(summary.treePriors, "tree priors", modelPath);
            assertNoBlankValues(summary.likelihoods, "likelihoods", modelPath);
            assertNoBlankValues(summary.operators, "operators", modelPath);
            assertNoBlankValues(summary.screenLoggers, "screen loggers", modelPath);
            assertNoBlankValues(summary.fileLoggers, "file loggers", modelPath);
            assertNoBlankValues(summary.treeLoggers, "tree loggers", modelPath);

            if (!summary.treePriors.isEmpty()) {
                assertTrue(
                        summary.operators.contains("UniformNodeHeightOperator"),
                        "Stochastic tree model should have a tree height operator: " + modelPath
                );

                assertTrue(
                        summary.operators.contains("ExchangeOperator"),
                        "Stochastic tree model should have an exchange operator: " + modelPath
                );

                assertTrue(
                        summary.operators.contains("WilsonBalding"),
                        "Stochastic tree model should have a Wilson-Balding operator: " + modelPath
                );
            }
        }
    }

    @Test
    public void representativeModelsCoverMultipleModelAxes() throws Exception {
        List<Path> modelPaths =
                representativeModelPaths();

        int modelsWithStateNodes =
                0;

        int modelsWithParameterPriors =
                0;

        int modelsWithTreePriors =
                0;

        int modelsWithLikelihoods =
                0;

        int modelsWithOperators =
                0;

        int modelsWithMCMCConfig =
                0;

        for (Path modelPath : modelPaths) {
            BeastXModel model =
                    buildModelFromFile(modelPath);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            if (!summary.stateNodes.isEmpty()) {
                modelsWithStateNodes++;
            }

            if (!summary.parameterPriors.isEmpty()) {
                modelsWithParameterPriors++;
            }

            if (!summary.treePriors.isEmpty()) {
                modelsWithTreePriors++;
            }

            if (!summary.likelihoods.isEmpty()) {
                modelsWithLikelihoods++;
            }

            if (!summary.operators.isEmpty()) {
                modelsWithOperators++;
            }

            if (summary.chainLength != 1
                    || !summary.screenLoggers.isEmpty()
                    || !summary.fileLoggers.isEmpty()
                    || !summary.treeLoggers.isEmpty()) {
                modelsWithMCMCConfig++;
            }
        }

        assertTrue(
                modelsWithStateNodes >= 4,
                "Representative models should include several stochastic parameter examples."
        );

        assertTrue(
                modelsWithParameterPriors >= 4,
                "Representative models should include several parameter-prior examples."
        );

        assertTrue(
                modelsWithTreePriors >= 4,
                "Representative models should include several stochastic tree-prior examples."
        );

        assertTrue(
                modelsWithLikelihoods >= 4,
                "Representative models should include several PhyloCTMC likelihood examples."
        );

        assertTrue(
                modelsWithOperators >= 4,
                "Representative models should include several models with MCMC operators."
        );

        assertTrue(
                modelsWithMCMCConfig >= 1,
                "Representative models should include at least one model with explicit MCMC configuration."
        );
    }

    @Test
    public void representativeModelsPrintSummariesForInspection() throws Exception {
        List<Path> modelPaths =
                representativeModelPaths();

        for (Path modelPath : modelPaths) {
            BeastXModel model =
                    buildModelFromFile(modelPath);

            BeastXModelSummary summary =
                    BeastXModelSummary.from(model);

            System.out.println(
                    summary.toReportString(
                            "Representative model: " + modelPath.getFileName()
                    )
            );
        }
    }

    @Test
    public void datedTipFBDRelaxedClockGTRBuildsExpectedShowcaseStructure() throws Exception {
        BeastXModelSummary summary =
                summaryForShowcase("datedTipFBDRelaxedClockGTR.phylospec");

        assertContainsAll(
                summary.stateNodes,
                "baseFrequencies",
                "branchRateCategories",
                "clockRate",
                "diversificationRate",
                "serialSamplingRate",
                "turnover",
                "rateAC",
                "rateAG",
                "rateAT",
                "rateCG",
                "rateCT",
                "rateGT"
        );

        assertContainsAll(
                summary.parameterPriors,
                "baseFrequencies_prior",
                "clockRate_prior",
                "diversificationRate_prior",
                "serialSamplingRate_prior",
                "turnover_prior",
                "rateAC_prior",
                "rateAG_prior",
                "rateAT_prior",
                "rateCG_prior",
                "rateCT_prior",
                "rateGT_prior"
        );

        assertEquals(
                List.of("tree"),
                summary.treeModels
        );

        assertEquals(
                List.of("tree_prior"),
                summary.treePriors
        );

        assertEquals(
                List.of("alignment_likelihood"),
                summary.likelihoods
        );

        assertContainsAll(
                summary.operators,
                "DeltaExchangeOperator",
                "ExchangeOperator",
                "RandomWalkIntegerOperator",
                "ScaleOperator",
                "SubtreeSlideOperator",
                "SwapOperator",
                "UniformIntegerOperator",
                "WilsonBalding"
        );

        assertAnyContains(summary.operatorDetails, "DeltaExchangeOperator(parameter=baseFrequencies");
        assertAnyContains(summary.operatorDetails, "RandomWalkIntegerOperator(parameter=branchRateCategories");
        assertAnyContains(summary.operatorDetails, "SwapOperator(parameter=branchRateCategories");
        assertAnyContains(summary.operatorDetails, "UniformIntegerOperator(parameter=branchRateCategories");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=clockRate");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=diversificationRate");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=serialSamplingRate");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=turnover");

        assertEquals(
                1,
                summary.chainLength
        );

        assertTrue(summary.screenLoggers.isEmpty());
        assertTrue(summary.fileLoggers.isEmpty());
        assertTrue(summary.treeLoggers.isEmpty());
    }

    @Test
    public void jointMolecularTraitMkModelBuildsTwoLikelihoodsOnSharedTree() throws Exception {
        BeastXModelSummary summary =
                summaryForShowcase("jointMolecularTraitMkPhyloCTMC.phylospec");

        assertContainsAll(
                summary.stateNodes,
                "baseFrequencies",
                "birthRate",
                "clockRate",
                "kappa",
                "traitRate"
        );

        assertContainsAll(
                summary.parameterPriors,
                "baseFrequencies_prior",
                "birthRate_prior",
                "clockRate_prior",
                "kappa_prior",
                "traitRate_prior"
        );

        assertEquals(
                List.of("tree"),
                summary.treeModels
        );

        assertEquals(
                List.of("tree_prior"),
                summary.treePriors
        );

        assertEquals(
                List.of(
                        "molecularAlignment_likelihood",
                        "traitAlignment_likelihood"
                ),
                summary.likelihoods
        );

        assertContainsAll(
                summary.operators,
                "DeltaExchangeOperator",
                "ExchangeOperator",
                "ScaleOperator",
                "SubtreeSlideOperator",
                "UpDownOperator",
                "WilsonBalding"
        );

        assertAnyContains(summary.operatorDetails, "DeltaExchangeOperator(parameter=baseFrequencies");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=birthRate");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=clockRate");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=kappa");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=traitRate");
        assertAnyContains(summary.operatorDetails, "UpDownOperator(up=[clockRate], down=[tree.allInternalNodeHeights]");

        assertEquals(
                1,
                summary.chainLength
        );

        assertTrue(summary.screenLoggers.isEmpty());
        assertTrue(summary.fileLoggers.isEmpty());
        assertTrue(summary.treeLoggers.isEmpty());
    }

    @Test
    public void partitionedGtrHkySiteClockMCMCBuildsConfiguredMCMCModel() throws Exception {
        BeastXModelSummary summary =
                summaryForShowcase("partitionedGtrHkySiteClockMCMC.phylospec");

        assertContainsAll(
                summary.stateNodes,
                "birthRate",
                "clockRate",
                "firstBaseFrequencies",
                "firstRateAC",
                "firstRateAG",
                "firstRateAT",
                "firstRateCG",
                "firstRateCT",
                "firstRateGT",
                "firstShape",
                "secondBaseFrequencies",
                "secondKappa",
                "secondShape"
        );

        assertEquals(
                List.of(
                        "firstAlignment_likelihood",
                        "secondAlignment_likelihood"
                ),
                summary.likelihoods
        );

        assertEquals(
                List.of("tree"),
                summary.treeModels
        );

        assertEquals(
                List.of("tree_prior"),
                summary.treePriors
        );

        assertContainsAll(
                summary.operators,
                "DeltaExchangeOperator",
                "ExchangeOperator",
                "ScaleOperator",
                "SubtreeSlideOperator",
                "UpDownOperator",
                "WilsonBalding"
        );

        assertTrue(
                Collections.frequency(summary.operators, "DeltaExchangeOperator") >= 2,
                "Partitioned GTR/HKY model should have separate simplex operators."
        );

        assertEquals(
                1,
                Collections.frequency(summary.operators, "UpDownOperator"),
                "A shared clock/tree target should have one coupled operator."
        );

        assertAnyContains(summary.operatorDetails, "DeltaExchangeOperator(parameter=firstBaseFrequencies");
        assertAnyContains(summary.operatorDetails, "DeltaExchangeOperator(parameter=secondBaseFrequencies");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=clockRate");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=firstShape");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=secondShape");
        assertAnyContains(summary.operatorDetails, "ScaleOperator(parameter=secondKappa");
        assertAnyContains(summary.operatorDetails, "UpDownOperator(up=[clockRate], down=[tree.allInternalNodeHeights]");

        assertEquals(
                50000,
                summary.chainLength
        );

        assertEquals(
                List.of(
                        "screenLogger(logEvery=5000, parameters=[birthRate, clockRate, firstShape, secondShape, secondKappa])"
                ),
                summary.screenLoggers
        );

        assertEquals(
                List.of(
                        "fileLogger(logEvery=5000, file=target/partitionedGtrHkySiteClockMCMC.log, parameters=[birthRate, clockRate, firstShape, secondShape, secondKappa])"
                ),
                summary.fileLoggers
        );

        assertEquals(
                List.of(
                        "treeLogger(logEvery=5000, file=target/partitionedGtrHkySiteClockMCMC.trees, trees=[tree])"
                ),
                summary.treeLoggers
        );
    }

    private List<Path> representativeModelPaths() throws Exception {
        assertTrue(
                Files.isDirectory(REPRESENTATIVE_MODEL_DIR),
                "Representative model directory does not exist: " + REPRESENTATIVE_MODEL_DIR
        );

        try (Stream<Path> paths = Files.walk(REPRESENTATIVE_MODEL_DIR)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".phylospec"))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }
    }

    private BeastXModel buildModelFromFile(Path path) throws Exception {
        String source =
                readSource(path);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        return runner.buildModel("test");
    }

    private BeastXModelSummary summaryForShowcase(String fileName) throws Exception {
        BeastXModel model =
                buildModelFromFile(SHOWCASE_MODEL_DIR.resolve(fileName));

        return BeastXModelSummary.from(model);
    }

    private String readSource(Path path) throws Exception {
        List<String> lines =
                Files.readAllLines(path, StandardCharsets.UTF_8);

        return String.join(System.lineSeparator(), stripExpectedBlocks(lines));
    }

    private List<String> stripExpectedBlocks(List<String> lines) {
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

    private boolean hasPhylogeneticContent(BeastXModelSummary summary) {
        return !summary.stateNodes.isEmpty()
                || !summary.parameterPriors.isEmpty()
                || !summary.treePriors.isEmpty()
                || !summary.likelihoods.isEmpty()
                || !summary.operators.isEmpty();
    }

    private void assertNoBlankValues(
            List<String> values,
            String label,
            Path modelPath
    ) {
        for (String value : values) {
            assertFalse(
                    value == null || value.isBlank(),
                    "Blank value found in " + label + " for model: " + modelPath
            );
        }
    }

    private void assertContainsAll(
            List<String> actual,
            String... expected
    ) {
        assertTrue(
                actual.containsAll(List.of(expected)),
                "Expected values were not all present.\nExpected: "
                        + List.of(expected)
                        + "\nActual: "
                        + actual
        );
    }

    private void assertAnyContains(
            List<String> actual,
            String expectedFragment
    ) {
        assertTrue(
                actual.stream().anyMatch(value -> value.contains(expectedFragment)),
                "Expected at least one value containing: "
                        + expectedFragment
                        + "\nActual: "
                        + actual
        );
    }
}
