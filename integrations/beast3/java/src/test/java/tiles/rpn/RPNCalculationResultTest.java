package tiles.rpn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class RPNCalculationResultTest {

    @Test
    void evaluatesBinaryAndUnaryOperators() {
        // (n - 1) * exp(log(x)) with n = 25 and x = 0.5
        RPNCalculationResult calculation = new RPNCalculationResult(
                "n one - x log exp *",
                List.of(
                        new IntScalarParam<>(25, Int.INSTANCE),
                        new IntScalarParam<>(1, Int.INSTANCE),
                        new RealScalarParam<>(0.5, Real.INSTANCE)),
                List.of("n", "one", "x"));

        assertEquals(12.0, calculation.evaluateConstant(), 1e-12);
    }
}
