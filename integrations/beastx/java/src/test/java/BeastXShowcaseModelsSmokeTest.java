import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXShowcaseModelsSmokeTest {

    private static final Path SHOWCASE_MODEL_DIR =
            Path.of("src", "test", "java", "tiling", "representative", "showcase");

    @Test
    public void datedTipFBDRelaxedClockGTRBuildsExpectedShowcaseStructure() throws Exception {
        BeastXModelSummary summary =
                summaryFor("datedTipFBDRelaxedClockGTR.phylospec");

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
                "NodeHeightScaleOperator",
                "RandomWalkOperator",
                "ScaleOperator",
                "SubtreeSlideOperator",
                "WilsonBalding"
        );

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
                summaryFor("jointMolecularTraitMkPhyloCTMC.phylospec");

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
                "NodeHeightScaleOperator",
                "ScaleOperator",
                "SubtreeSlideOperator",
                "UpDownOperator",
                "WilsonBalding"
        );

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
                summaryFor("partitionedGtrHkySiteClockMCMC.phylospec");

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
                "NodeHeightScaleOperator",
                "ScaleOperator",
                "SubtreeSlideOperator",
                "UpDownOperator",
                "WilsonBalding"
        );

        assertTrue(
                Collections.frequency(summary.operators, "DeltaExchangeOperator") >= 2,
                "Partitioned GTR/HKY model should have separate simplex operators."
        );

        assertTrue(
                Collections.frequency(summary.operators, "UpDownOperator") >= 2,
                "Partitioned clock/site model should have coupled clock-tree operators."
        );

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

    private BeastXModelSummary summaryFor(String fileName) throws Exception {
        Path path =
                SHOWCASE_MODEL_DIR.resolve(fileName);

        String source =
                readSource(path);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        return BeastXModelSummary.from(model);
    }

    private String readSource(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
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
}