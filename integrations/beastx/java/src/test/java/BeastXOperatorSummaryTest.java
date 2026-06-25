import org.junit.jupiter.api.Test;
import tiling.BeastXState;
import tiling.operators.OperatorBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXOperatorSummaryTest {

    private static final Path H1N1_SOURCE = Path.of(
            "src/test/java/resources/comparison/tutorialh1n1datedexponentialcoalescenthkygamma.phylospec"
    );

    @Test
    void printsBeastXOperatorsForH1N1CoalescentHKYGammaModel() throws Exception {
        String source = Files.readString(H1N1_SOURCE);

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXState beastState =
                runner.buildState("h1n1-beastx-operator-summary");

        List<String> operatorSummary =
                new OperatorBuilder().summarize(beastState);

        System.out.println();
        System.out.println("==== BEAST X operator summary ====");
        operatorSummary.forEach(System.out::println);
        System.out.println("==== end BEAST X operator summary ====");
        System.out.println();

        assertContains(operatorSummary, "ScaleOperator(parameter=clockRate");
        assertContains(operatorSummary, "ScaleOperator(parameter=populationSize");
        assertContains(operatorSummary, "ScaleOperator(parameter=growthRate");
        assertContains(operatorSummary, "ScaleOperator(parameter=kappa");
        assertContains(operatorSummary, "ScaleOperator(parameter=gammaShape");

        assertContains(operatorSummary, "DeltaExchangeOperator(parameter=baseFrequencies");

        assertContains(operatorSummary, "NodeHeightScaleOperator(tree=tree");
        assertContains(operatorSummary, "ExchangeOperator(tree=tree, mode=narrow");
        assertContains(operatorSummary, "ExchangeOperator(tree=tree, mode=wide");
        assertContains(operatorSummary, "WilsonBalding(tree=tree");

        assertTrue(
                contains(operatorSummary, "SubtreeSlideOperator(tree=tree")
                        || contains(operatorSummary, "SubtreeSlideOperator"),
                "Expected BEAST X to include a subtree-slide-style tree operator."
        );

        assertContains(operatorSummary, "UpDownOperator(up=[clockRate], down=[tree");
    }

    private static void assertContains(List<String> summaries, String expectedFragment) {
        assertTrue(
                contains(summaries, expectedFragment),
                "Expected operator summary to contain: " + expectedFragment
                        + "\nActual summary:\n" + String.join("\n", summaries)
        );
    }

    private static boolean contains(List<String> summaries, String expectedFragment) {
        return summaries.stream()
                .anyMatch(summary -> summary.contains(expectedFragment));
    }
}
