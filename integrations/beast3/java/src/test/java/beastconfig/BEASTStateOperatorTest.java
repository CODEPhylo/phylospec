package beastconfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import org.junit.jupiter.api.Test;

class BEASTStateOperatorTest {

    @Test
    void removingOperatorDetachesItsInputReferences() {
        BEASTState state = new BEASTState("operator-removal");
        RealScalarParam<PositiveReal> parameter = new RealScalarParam<>(0.5, PositiveReal.INSTANCE);
        parameter.setID("sigma2");
        ScaleOperator scale = new ScaleOperator();
        state.setInput(scale, scale.parameterInput, parameter);
        state.addOperator(scale, parameter);

        assertTrue(state.operators.contains(scale));
        assertTrue(parameter.getOutputs().contains(scale));
        assertTrue(state.removeOperator(scale));
        assertFalse(state.operators.contains(scale));
        assertFalse(parameter.getOutputs().contains(scale));
        assertFalse(state.removeOperator(scale));
        state.initializeBEASTObjects();
    }
}
