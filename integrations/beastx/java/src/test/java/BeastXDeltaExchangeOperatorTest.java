import dr.app.beast.BeastParser;
import dr.app.beast.BeastVersion;
import dr.inference.model.Parameter;
import dr.inference.operators.DeltaExchangeOperator;
import dr.math.MathUtils;
import org.junit.jupiter.api.Test;
import tiling.BeastXState;
import tiling.operators.OperatorBuilder;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BeastXDeltaExchangeOperatorTest {

    @Test
    public void simplexProposalsMatchNativeXmlDefaultsAndRestoreRejectedValues() throws Exception {
        DeltaExchangeOperator actual = actualOperator();
        DeltaExchangeOperator reference = nativeXmlOperator();
        Parameter actualParameter = actual.getParameter();
        Parameter referenceParameter = reference.getParameter();
        assertEquals(reference.getWeight(), actual.getWeight());
        assertEquals(reference.getRawParameter(), actual.getRawParameter());
        assertEquals(reference.getMode(), actual.getMode());

        double[] starting = {0.1, 0.2, 0.3, 0.4};
        for (long seed = 1; seed <= 20; seed++) {
            reset(actualParameter, starting);
            reset(referenceParameter, starting);
            actualParameter.storeParameterValues();
            referenceParameter.storeParameterValues();

            MathUtils.setSeed(seed);
            double actualHastings = actual.operate();
            MathUtils.setSeed(seed);
            double referenceHastings = reference.operate();
            assertEquals(referenceHastings, actualHastings);
            assertTrue(Double.isFinite(actualHastings));
            double[] proposed = actualParameter.getParameterValues();
            assertFalse(Arrays.equals(starting, proposed));
            assertArrayEquals(referenceParameter.getParameterValues(), proposed);
            assertSimplex(actualParameter);

            if (seed % 2 == 0) {
                actual.accept(0.0);
                reference.accept(0.0);
                actualParameter.acceptParameterValues();
                referenceParameter.acceptParameterValues();
                assertArrayEquals(proposed, actualParameter.getParameterValues());
            } else {
                actual.reject();
                reference.reject();
                actualParameter.restoreParameterValues();
                referenceParameter.restoreParameterValues();
                assertArrayEquals(starting, actualParameter.getParameterValues());
            }
            assertArrayEquals(referenceParameter.getParameterValues(), actualParameter.getParameterValues());
            assertSimplex(actualParameter);
        }
        assertEquals(10, actual.getAcceptCount());
        assertEquals(10, actual.getRejectCount());
    }

    @Test
    public void boundaryProposalsRejectNormallyAndPreserveTheSimplex() throws Exception {
        DeltaExchangeOperator actual = actualOperator();
        Parameter parameter = actual.getParameter();
        double[] boundary = {0.0, 1.0, 0.0, 0.0};
        int rejected = 0;
        int accepted = 0;
        for (long seed = 1; seed <= 40; seed++) {
            reset(parameter, boundary);
            parameter.storeParameterValues();
            MathUtils.setSeed(seed);
            double logHastings = actual.operate();
            if (logHastings == Double.NEGATIVE_INFINITY) {
                rejected++;
                assertArrayEquals(boundary, parameter.getParameterValues());
                actual.reject();
                parameter.restoreParameterValues();
                assertArrayEquals(boundary, parameter.getParameterValues());
            } else {
                accepted++;
                assertTrue(Double.isFinite(logHastings));
                assertFalse(Arrays.equals(boundary, parameter.getParameterValues()));
                actual.accept(0.0);
                parameter.acceptParameterValues();
            }
            assertSimplex(parameter);
        }
        assertTrue(rejected > 0);
        assertTrue(accepted > 0);
    }

    private static void assertSimplex(Parameter parameter) {
        double sum = 0.0;
        for (double value : parameter.getParameterValues()) {
            assertTrue(value >= 0.0 && value <= 1.0);
            sum += value;
        }
        assertEquals(1.0, sum, 1.0e-14);
        assertTrue(parameter.isWithinBounds());
    }

    private static void reset(Parameter parameter, double[] values) {
        for (int index = 0; index < values.length; index++) {
            parameter.setParameterValueQuietly(index, values[index]);
        }
        parameter.fireParameterChangedEvent();
    }

    private static DeltaExchangeOperator actualOperator() throws Exception {
        BeastXState state = new PhyloSpecRunner("""
                Simplex frequencies ~ Dirichlet(concentration=[1.0, 1.0, 1.0, 1.0])
                """).buildState("simplexOperator");
        List<dr.inference.operators.MCMCOperator> operators = new OperatorBuilder().build(state);
        assertEquals(1, operators.size());
        DeltaExchangeOperator operator = assertInstanceOf(DeltaExchangeOperator.class, operators.getFirst());
        assertSame(state.stateNodesByPhyloSpecName.get("frequencies"), operator.getParameter());
        return operator;
    }

    private static DeltaExchangeOperator nativeXmlOperator() throws Exception {
        BeastParser parser = new BeastParser(
                new String[0], List.of(), false, false, false, BeastVersion.INSTANCE);
        // No parameterWeights attribute: exercise the native parser's unit-weight default.
        String source = """
                <beast>
                    <parameter id="frequencies" value="0.25 0.25 0.25 0.25" lower="0.0" upper="1.0"/>
                    <deltaExchange delta="0.01" weight="1.0">
                        <parameter idref="frequencies"/>
                    </deltaExchange>
                </beast>
                """;
        return assertInstanceOf(
                DeltaExchangeOperator.class,
                parser.parse(new StringReader(source), DeltaExchangeOperator.class));
    }
}
