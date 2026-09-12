package operators.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import org.junit.jupiter.api.Test;

public class ModelIndicatorOperatorTest {

    @Test
    public void proposesADifferentValidIndex() {
        IntScalarParam<NonNegativeInt> indicator =
                new IntScalarParam<>(0, NonNegativeInt.INSTANCE);
        ModelIndicatorOperator operator = operator(indicator, 3);

        for (int i = 0; i < 100; i++) {
            int previous = indicator.get();
            assertEquals(0.0, operator.proposal());
            assertNotEquals(previous, indicator.get());
            assertTrue(indicator.get() >= 0 && indicator.get() < 3);
        }
    }

    @Test
    public void rejectsProposalWhenOnlyOneModelExists() {
        IntScalarParam<NonNegativeInt> indicator =
                new IntScalarParam<>(0, NonNegativeInt.INSTANCE);
        ModelIndicatorOperator operator = operator(indicator, 1);

        assertEquals(Double.NEGATIVE_INFINITY, operator.proposal());
        assertEquals(0, indicator.get());
    }

    private static ModelIndicatorOperator operator(
            IntScalarParam<NonNegativeInt> indicator, int modelCount) {
        ModelIndicatorOperator operator = new ModelIndicatorOperator();
        operator.indicatorInput.setValue(indicator, operator);
        operator.modelCountInput.setValue(modelCount, operator);
        operator.initAndValidate();
        return operator;
    }
}
