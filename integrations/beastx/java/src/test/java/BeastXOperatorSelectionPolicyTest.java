import org.junit.jupiter.api.Test;
import tiling.BeastXState;
import tiling.operators.OperatorSelector;
import tiling.operators.OperatorSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeastXOperatorSelectionPolicyTest {

    @Test
    public void strictClockUsesConfiguredBeautiOperatorSchedule() throws Exception {
        String source =
                """
                Alignment data = fromNexus("src/test/java/resources/primate-mtDNA.nex")
                Taxa taxa = taxa(data)

                Tree tree ~ Yule(
                    birthRate=1.0,
                    taxa=taxa
                )

                Rate clockRate ~ LogNormal(
                    logMean=0.0,
                    logSd=1.0
                )

                Alignment alignment ~ PhyloCTMC(
                    tree=tree,
                    qMatrix=jc69(),
                    branchRates~StrictClock(
                        clockRate=clockRate,
                        tree=tree
                    )
                ) observed as data

                mcmc {
                    Real clockRateOperatorWeight = 6.0
                    Real treeClockUpDownWeight = 10.0
                    Real treeClockUpDownScaleFactor = 0.6
                }
                """;

        BeastXState state =
                new PhyloSpecRunner(source)
                        .buildState("strictClockOperatorPolicy");

        List<OperatorSpec> operators =
                new OperatorSelector().select(state);

        assertEquals(
                List.of(
                        OperatorSpec.Family.SCALE,
                        OperatorSpec.Family.TREE_SUBTREE_LEAP,
                        OperatorSpec.Family.TREE_FIXED_HEIGHT_SPR,
                        OperatorSpec.Family.TREE_CLOCK_UP_DOWN
                ),
                operators.stream()
                        .map(OperatorSpec::family)
                        .toList()
        );

        assertParameterOperator(
                operators,
                OperatorSpec.Family.SCALE,
                "clockRate",
                6.0,
                0.75
        );

        assertTreeOperator(
                operators,
                OperatorSpec.Family.TREE_SUBTREE_LEAP,
                "tree",
                30.0,
                1.0
        );

        assertTreeOperator(
                operators,
                OperatorSpec.Family.TREE_FIXED_HEIGHT_SPR,
                "tree",
                3.0,
                0.0
        );

        OperatorSpec upDown =
                findOperator(
                        operators,
                        OperatorSpec.Family.TREE_CLOCK_UP_DOWN
                );

        assertEquals("clockRate", upDown.parameter().getId());
        assertEquals("tree", upDown.tree().getId());
        assertEquals(10.0, upDown.weight());
        assertEquals(0.6, upDown.tuning());
    }

    private static void assertParameterOperator(
            List<OperatorSpec> operators,
            OperatorSpec.Family family,
            String parameterId,
            double weight,
            double tuning
    ) {
        OperatorSpec operator =
                findOperator(operators, family);

        assertEquals(parameterId, operator.parameter().getId());
        assertEquals(weight, operator.weight());
        assertEquals(tuning, operator.tuning());
    }

    private static void assertTreeOperator(
            List<OperatorSpec> operators,
            OperatorSpec.Family family,
            String treeId,
            double weight,
            double tuning
    ) {
        OperatorSpec operator =
                findOperator(operators, family);

        assertEquals(treeId, operator.tree().getId());
        assertEquals(weight, operator.weight());
        assertEquals(tuning, operator.tuning());
    }

    private static OperatorSpec findOperator(
            List<OperatorSpec> operators,
            OperatorSpec.Family family
    ) {
        return operators.stream()
                .filter(operator -> operator.family() == family)
                .findFirst()
                .orElseThrow();
    }
}
