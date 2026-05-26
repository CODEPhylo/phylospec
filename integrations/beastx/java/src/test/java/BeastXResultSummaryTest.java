import org.junit.jupiter.api.Test;
import tiling.BeastXModel;
import tiling.BeastXModelSummary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXResultSummaryTest {

    @Test
    public void summarizesHKYPhyloCTMCWithInlinePriors() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/withInlinePriors.phylospec");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertEquals(2, summary.stateNodes.size());
        assertEquals(2, summary.parameterPriors.size());
        assertEquals(1, summary.treeModels.size());
        assertEquals(1, summary.treePriors.size());
        assertEquals(1, summary.likelihoods.size());
        assertEquals(7, summary.operators.size());

        assertTrue(summary.stateNodes.contains("kappa"));
        assertTrue(summary.stateNodes.contains("baseFrequencies"));

        assertTrue(summary.parameterPriors.contains("kappa_prior"));
        assertTrue(summary.parameterPriors.contains("baseFrequencies_prior"));

        assertTrue(summary.treeModels.contains("tree"));
        assertTrue(summary.treePriors.contains("tree_prior"));
        assertTrue(summary.likelihoods.contains("alignment_likelihood"));

        assertTrue(summary.operators.contains("ScaleOperator"));
        assertTrue(summary.operators.contains("DeltaExchangeOperator"));
        assertTrue(summary.operators.contains("NodeHeightScaleOperator"));
        assertTrue(summary.operators.contains("SubtreeSlideOperator"));
        assertTrue(summary.operators.contains("ExchangeOperator"));
        assertTrue(summary.operators.contains("WilsonBalding"));


        System.out.println(summary.toReportString("HKY + priors + PhyloCTMC"));
    }

    @Test
    public void summarizesStrictClockPhyloCTMCWithClockPrior() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/phyloctmc/withStrictClockPrior.phylospec");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertEquals(1, summary.stateNodes.size());
        assertEquals(1, summary.parameterPriors.size());
        assertEquals(1, summary.treeModels.size());
        assertEquals(1, summary.treePriors.size());
        assertEquals(1, summary.likelihoods.size());
        assertEquals(7, summary.operators.size());

        assertTrue(summary.stateNodes.contains("clockRate"));
        assertTrue(summary.parameterPriors.contains("clockRate_prior"));

        assertTrue(summary.treeModels.contains("tree"));
        assertTrue(summary.treePriors.contains("tree_prior"));
        assertTrue(summary.likelihoods.contains("alignment_likelihood"));

        assertTrue(summary.operators.contains("ScaleOperator"));
        assertTrue(summary.operators.contains("NodeHeightScaleOperator"));
        assertTrue(summary.operators.contains("SubtreeSlideOperator"));
        assertTrue(summary.operators.contains("ExchangeOperator"));
        assertTrue(summary.operators.contains("WilsonBalding"));
        assertTrue(summary.operators.contains("UpDownOperator"));

        System.out.println(summary.toReportString("Strict clock + clock prior + PhyloCTMC"));
    }

    @Test
    public void summarizesCoalescentWithExponentialPopulationFunctionPriors() throws Exception {
        BeastXModel model =
                buildModelFromFile("src/test/java/tiling/treepriors/coalescentWithExponentialPopulationFunctionPrior.phylospec");

        BeastXModelSummary summary =
                BeastXModelSummary.from(model);

        assertEquals(2, summary.stateNodes.size());
        assertEquals(2, summary.parameterPriors.size());
        assertEquals(1, summary.treeModels.size());
        assertEquals(1, summary.treePriors.size());
        assertEquals(0, summary.likelihoods.size());
        assertEquals(7, summary.operators.size());

        assertTrue(summary.stateNodes.contains("populationSize"));
        assertTrue(summary.stateNodes.contains("growthRate"));

        assertTrue(summary.parameterPriors.contains("populationSize_prior"));
        assertTrue(summary.parameterPriors.contains("growthRate_prior"));

        assertTrue(summary.treeModels.contains("tree"));
        assertTrue(summary.treePriors.contains("tree_prior"));

        assertTrue(summary.operators.contains("ScaleOperator"));
        assertTrue(summary.operators.contains("RandomWalkOperator"));
        assertTrue(summary.operators.contains("NodeHeightScaleOperator"));
        assertTrue(summary.operators.contains("SubtreeSlideOperator"));
        assertTrue(summary.operators.contains("ExchangeOperator"));
        assertTrue(summary.operators.contains("WilsonBalding"));

        System.out.println(summary.toReportString("Coalescent + exponential population function priors"));
    }

    private BeastXModel buildModelFromFile(String path) throws Exception {
        String source =
                readSource(path);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        return runner.buildModel("test");
    }

    private String readSource(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8)
                .lines()
                .takeWhile(line -> !line.trim().startsWith("// EXPECTED_"))
                .collect(Collectors.joining("\n"));
    }
}