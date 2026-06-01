import dr.inference.model.Statistic;
import org.junit.jupiter.api.Test;
import tiling.BeastXModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXRPNCalculationTest {

    @Test
    public void buildsAndEvaluatesDeterministicRPNCalculation() throws Exception {
        String source =
                """
                Real x ~ Normal(mean=1.0, sd=2.0)
                Real y ~ Normal(mean=1.0, sd=2.0)
                Real z = x + y
                """;

        PhyloSpecRunner runner =
                new PhyloSpecRunner(source);

        BeastXModel model =
                runner.buildModel("test");

        assertEquals(2, model.beastState.stateNodes.size());
        assertEquals(2, model.beastState.priorDistributions.size());
        assertEquals(1, model.beastState.calculationNodes.size());

        Statistic z =
                model.beastState.calculationNodesByPhyloSpecName.get("z");

        assertTrue(
                Double.isFinite(z.getStatisticValue(0)),
                "Expected deterministic RPN calculation to evaluate to a finite value."
        );
    }
}