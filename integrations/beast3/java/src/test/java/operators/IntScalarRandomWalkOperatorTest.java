package operators;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.util.Randomizer;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IntScalarRandomWalkOperatorTest {

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void usesTheNativeUnitWindowKernelAndReportsTheUpdatedState(int step) {
        IntScalarParam<NonNegativeInt> parameter = new IntScalarParam<>(2, NonNegativeInt.INSTANCE);
        IntScalarRandomWalkOperator operator = operator(parameter);
        Randomizer.setSeed(seedForStep(step));

        assertEquals(step == 0 ? Double.NEGATIVE_INFINITY : 0.0, operator.proposal());
        assertEquals(2 + step, parameter.get());
        assertEquals(List.of(parameter), operator.listStateNodes());
    }

    @ParameterizedTest
    @CsvSource({"0,-1", "2147483647,1", "-2147483648,-1"})
    void invalidBoundaryMovesAreRejectedWithoutRedrawingOrOverflow(int initial, int step) {
        IntScalarParam<?> parameter = initial == Integer.MIN_VALUE
                ? new IntScalarParam<>(initial, Int.INSTANCE)
                : new IntScalarParam<>(initial, NonNegativeInt.INSTANCE);
        IntScalarRandomWalkOperator operator = operator(parameter);
        Randomizer.setSeed(seedForStep(step));

        assertEquals(Double.NEGATIVE_INFINITY, operator.proposal());
        assertEquals(initial, parameter.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void respectsNarrowerDomains(int step) {
        IntScalarParam<PositiveInt> parameter = new IntScalarParam<>(1, PositiveInt.INSTANCE);
        IntScalarRandomWalkOperator operator = operator(parameter);
        Randomizer.setSeed(seedForStep(step));

        assertEquals(step < 0 ? Double.NEGATIVE_INFINITY : 0.0, operator.proposal());
        assertEquals(step < 0 ? 1 : 2, parameter.get());
    }

    private static IntScalarRandomWalkOperator operator(IntScalarParam<?> parameter) {
        IntScalarRandomWalkOperator operator = new IntScalarRandomWalkOperator();
        operator.initByName("parameter", parameter, "weight", 10.0);
        return operator;
    }

    private static long seedForStep(int step) {
        for (long seed = 1; seed <= 1000; seed++) {
            Randomizer.setSeed(seed);
            if (Randomizer.nextInt(3) - 1 == step) return seed;
        }
        throw new AssertionError("No seed for step " + step);
    }
}
