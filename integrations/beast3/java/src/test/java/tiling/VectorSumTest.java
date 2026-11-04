package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Input;
import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.type.IntVector;
import beast.base.spec.type.RealVector;
import beast.base.spec.type.Tensor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VectorSumTest {

    @Test
    void realSumPreservesTheVectorAndUsesTheRealDomain() {
        RealVectorParam<Real> vector = new RealVectorParam<>(new double[] {2.5, -3.0, 0.5}, Real.INSTANCE);
        RealVectorSum sum = realSum(vector);

        assertSame(vector, sum.vectorInput.get());
        assertTrue(vector.getOutputs().contains(sum));
        assertSame(Real.INSTANCE, sum.getDomain());
        assertEquals(0.0, sum.get(), 0.0);
        assertEquals("sum\t0.0\t", logged(sum, true));
        assertEquals(
                -4.5,
                realSum(new RealVectorParam<>(new double[] {-4.5}, Real.INSTANCE))
                        .get(),
                0.0);

        RealVectorSum positive = realSum(new RealVectorParam<>(new double[] {1.0, 2.0}, PositiveReal.INSTANCE));
        assertSame(Real.INSTANCE, positive.getDomain());
        assertEquals(3.0, positive.get(), 0.0);
    }

    @Test
    void integerSumPreservesTheVectorAndLogsNegativeValuesExactly() {
        IntVectorParam<Int> vector = integerVector(-2, -4);
        IntVectorSum sum = intSum(vector);

        assertSame(vector, sum.vectorInput.get());
        assertTrue(vector.getOutputs().contains(sum));
        assertSame(Int.INSTANCE, sum.getDomain());
        assertEquals(-6, sum.get());
        assertEquals("sum\t-6\t", logged(sum, true));

        IntVectorSum nonNegative = intSum(new IntVectorParam<>(new int[] {0, 2, 3}, NonNegativeInt.INSTANCE));
        assertSame(Int.INSTANCE, nonNegative.getDomain());
        assertEquals(5, nonNegative.get());
    }

    @Test
    void integerSumChecksTheFinalResultAfterExactAccumulation() {
        assertEquals(Integer.MIN_VALUE, intSum(integerVector(Integer.MIN_VALUE)).get());
        assertEquals(Integer.MAX_VALUE, intSum(integerVector(Integer.MAX_VALUE)).get());
        assertEquals(
                Integer.MAX_VALUE,
                intSum(integerVector(Integer.MAX_VALUE, 1, -1)).get());
        assertEquals(
                Integer.MIN_VALUE,
                intSum(integerVector(Integer.MIN_VALUE, -1, 1)).get());
        assertEquals(
                -1, intSum(integerVector(Integer.MAX_VALUE, Integer.MIN_VALUE)).get());

        IntVectorSum positiveOverflow = intSum(integerVector(Integer.MAX_VALUE, 1));
        IntVectorSum negativeOverflow = intSum(integerVector(Integer.MIN_VALUE, -1));
        assertTrue(assertThrows(ArithmeticException.class, positiveOverflow::get)
                .getMessage()
                .contains("2147483648"));
        assertTrue(assertThrows(ArithmeticException.class, negativeOverflow::get)
                .getMessage()
                .contains("-2147483649"));

        // An unsuccessful read must not leave a partial or overflowing result cached.
        ((IntVectorParam<?>) positiveOverflow.vectorInput.get()).set(1, 0);
        assertEquals(Integer.MAX_VALUE, positiveOverflow.get());
        assertEquals("2147483647\t", logged(positiveOverflow, false));
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void liveSumFollowsNativeStateAcceptanceAndRejection(boolean integer, boolean posteriorUsesSum) {
        StateNode vector =
                integer ? integerVector(-3, 1) : new RealVectorParam<>(new double[] {-3.0, 1.0}, Real.INSTANCE);
        vector.setID("vector");
        CalculationNode sum = integer ? intSum((IntVector<?>) vector) : realSum((RealVector<?>) vector);
        Tensor<?, ?> sumValue = (Tensor<?, ?>) sum;
        Loggable logger = (Loggable) sum;

        TensorDensity posterior = new TensorDensity();
        posterior.initByName("value", posteriorUsesSum ? sumValue : (Tensor<?, ?>) vector);
        State state = new State();
        state.initByName("stateNode", List.of(vector));
        state.initialise();
        state.setPosterior(posterior);

        // Prime get() before proposals, including when the sum is only used for logging.
        assertEquals(-2.0, numericValue(sumValue), 0.0);
        assertEquals(expectedLogP(-3, posteriorUsesSum), state.robustlyCalcPosterior(posterior), 0.0);

        int[] proposals = {2, 5, 4};
        boolean[] decisions = {true, false, true};
        int currentFirst = -3;
        for (int step = 0; step < decisions.length; step++) {
            double previousLogP = posterior.getCurrentLogP();
            int previousFirst = currentFirst;

            state.store(step + 1);
            if (vector instanceof RealVectorParam<?> real) {
                real.set(0, proposals[step]);
            } else {
                ((IntVectorParam<?>) vector).set(0, proposals[step]);
            }
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertEquals(posteriorUsesSum, state.getCurrentCalculationNodes().contains(sum));

            double proposedLogP = posterior.calculateLogP();
            assertEquals(expectedLogP(proposals[step], posteriorUsesSum), proposedLogP, 0.0);
            assertEquals(proposals[step] + 1.0, numericValue(sumValue), 0.0);
            assertEquals(
                    proposals[step] + 1.0,
                    Double.parseDouble(logged(logger, false).trim()),
                    0.0);

            if (decisions[step]) {
                state.acceptCalculationNodes();
                currentFirst = proposals[step];
            } else {
                state.restore();
                state.restoreCalculationNodes();
                currentFirst = previousFirst;
            }
            state.setEverythingDirty(false);

            assertEquals(currentFirst, ((Number) ((Tensor<?, ?>) vector).get(0)).intValue());
            assertEquals(currentFirst + 1.0, numericValue(sumValue), 0.0);
            assertEquals(
                    currentFirst + 1.0, Double.parseDouble(logged(logger, false).trim()), 0.0);
            assertEquals(decisions[step] ? proposedLogP : previousLogP, posterior.getCurrentLogP(), 0.0);
            assertEquals(expectedLogP(currentFirst, posteriorUsesSum), posterior.calculateLogP(), 0.0);
        }
    }

    private static RealVectorSum realSum(RealVector<? extends Real> vector) {
        RealVectorSum sum = new RealVectorSum();
        sum.setID("sum");
        sum.initByName("vector", vector);
        return sum;
    }

    private static IntVectorSum intSum(IntVector<? extends Int> vector) {
        IntVectorSum sum = new IntVectorSum();
        sum.setID("sum");
        sum.initByName("vector", vector);
        return sum;
    }

    private static IntVectorParam<Int> integerVector(int... values) {
        IntVectorParam<Int> vector = new IntVectorParam<>();
        vector.setLower(Integer.MIN_VALUE);
        vector.setUpper(Integer.MAX_VALUE);
        vector.initByName(
                "domain", Int.INSTANCE, "value", IntStream.of(values).boxed().toList());
        return vector;
    }

    private static double numericValue(Tensor<?, ?> value) {
        return ((Number) value.get(0)).doubleValue();
    }

    private static double expectedLogP(int first, boolean posteriorUsesSum) {
        return posteriorUsesSum ? -0.5 * (first + 1.0) * (first + 1.0) : -0.5 * (first * first + 1.0);
    }

    private static String logged(Loggable value, boolean includeHeader) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            if (includeHeader) value.init(out);
            value.log(0, out);
            value.close(out);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** A test density that makes either the scalar or its source vector a posterior ancestor. */
    public static final class TensorDensity extends Distribution {
        public final Input<Tensor<?, ?>> valueInput =
                new Input<>("value", "Numeric value used by the test density.", Input.Validate.REQUIRED);

        @Override
        public double calculateLogP() {
            logP = 0.0;
            Tensor<?, ?> value = valueInput.get();
            for (int i = 0; i < value.size(); i++) {
                double element = ((Number) value.get(i)).doubleValue();
                logP -= 0.5 * element * element;
            }
            return logP;
        }

        @Override
        public List<String> getArguments() {
            return List.of();
        }

        @Override
        public List<String> getConditions() {
            return List.of();
        }

        @Override
        public void sample(State state, Random random) {
            throw new UnsupportedOperationException("The test density is only evaluated.");
        }
    }
}
