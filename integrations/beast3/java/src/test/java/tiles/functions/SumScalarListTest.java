package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;
import static tiles.functions.SumTileTest.compile;

import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tiling.IntVectorFromScalars;
import tiling.IntVectorSum;
import tiling.RealVectorFromScalars;
import tiling.RealVectorSum;

class SumScalarListTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({
        "false,false,false", "false,false,true", "false,true,false", "false,true,true",
        "true,false,false", "true,false,true", "true,true,false", "true,true,true"
    })
    void sharedScalarSumsFollowPosteriorAcceptanceRestorationAndLogging(
            boolean integer, boolean loggerOnly, boolean acceptFirst) throws Exception {
        String type = integer ? "Integer" : "Real";
        String prior = integer ? "DiscreteUniform(lower=1,upper=9)" : "Uniform(lower=1.0,upper=9.0)";
        String source = "%s a ~ %s\n%s b ~ %s\nVector<%s> values = [a,b,a]\n".formatted(type, prior, type, prior, type);
        String likelihoods = loggerOnly ? "" : integer ? """
                Integer y1 ~ DiscreteUniform(lower=0,upper=total) observed as 4
                Integer y2 ~ DiscreteUniform(lower=0,upper=copy) observed as 3
                """ : """
                Real y1 ~ Normal(mean=total,sd=1.0) observed as 4.0
                Real y2 ~ Normal(mean=copy,sd=2.0) observed as 3.0
                """;
        BEASTState baseline = compile(source);
        BEASTState graph = compile(
                source + "%s total = sum(vector=values)\n%s copy = total\n".formatted(type, type) + likelihoods + """
                mcmc {
                    Logger trace = fileLogger(logEvery=1,file="sum-list.log",parameters=[total,copy])
                }
                """);
        assertUnchangedSamplingGraph(baseline, graph);
        assertEquals(2, graph.stateNodes.size());
        assertEquals(1, graph.calculationNodes.size());
        assertEquals(loggerOnly ? 0 : 2, graph.likelihoodDistributions.size());
        StateNode a = parameter(graph, "a");
        StateNode b = parameter(graph, "b");
        CalculationNode sum = graph.calculationNodes.keySet().iterator().next();
        CalculationNode vector;
        List<?> elements;
        if (integer) {
            IntVectorSum total = assertInstanceOf(IntVectorSum.class, sum);
            IntVectorFromScalars input = assertInstanceOf(IntVectorFromScalars.class, total.vectorInput.get());
            vector = input;
            elements = input.valuesInput.get();
            assertSame(Int.INSTANCE, total.getDomain());
            assertEquals(3, input.size());
            assertEquals(List.of(5, 5, 5), input.getElements());
        } else {
            RealVectorSum total = assertInstanceOf(RealVectorSum.class, sum);
            RealVectorFromScalars input = assertInstanceOf(RealVectorFromScalars.class, total.vectorInput.get());
            vector = input;
            elements = input.valuesInput.get();
            assertSame(Real.INSTANCE, total.getDomain());
            assertEquals(3, input.size());
            assertEquals(List.of(5.0, 5.0, 5.0), input.getElements());
        }
        assertEquals(List.of(a, b, a), elements);
        assertSame(elements.getFirst(), elements.getLast());
        assertTrue(a.getOutputs().contains(vector));
        assertTrue(b.getOutputs().contains(vector));
        assertTrue(vector.getOutputs().contains(sum));
        assertSame(sum, graph.fileLoggerSpecs.getFirst().parameters().get(0));
        assertSame(sum, graph.fileLoggerSpecs.getFirst().parameters().get(1));
        for (Distribution likelihood : graph.likelihoodDistributions) {
            assertSame(
                    sum, integer ? ((IntUniform) likelihood).upperInput.get() : ((Normal) likelihood).meanInput.get());
            assertTrue(sum.getOutputs().contains(likelihood));
        }

        CompoundDistribution posterior = posterior(graph);
        State state = state(graph, posterior);
        assertEquals(
                expectedPosterior(integer, loggerOnly, number(a), number(b)),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        assertEquals(15.0, number(sum), 0.0);
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < decisions.length; step++) {
            StateNode changed = step == 1 ? b : a;
            double previousValue = number(changed);
            double previousPosterior = posterior.getCurrentLogP();
            List<Double> previousLikelihoods = likelihoodValues(graph);
            state.store(step + 1);
            set(changed, step + (integer ? 2.0 : 2.25));
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertEquals(!loggerOnly, state.getCurrentCalculationNodes().contains(vector));
            assertEquals(!loggerOnly, state.getCurrentCalculationNodes().contains(sum));
            double proposed = posterior.calculateLogP();
            double expectedSum = 2 * number(a) + number(b);
            assertEquals(expectedSum, number(sum), 0.0);
            assertEquals(expectedSum, logged(sum), 0.0);
            assertEquals(expectedPosterior(integer, loggerOnly, number(a), number(b)), proposed, TOLERANCE);
            if (!loggerOnly) assertNotEquals(previousPosterior, proposed, TOLERANCE);
            List<Double> proposedLikelihoods = likelihoodValues(graph);
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
                assertEquals(previousValue, number(changed), 0.0);
            }
            state.setEverythingDirty(false);
            assertEquals(decisions[step] ? proposed : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(decisions[step] ? proposedLikelihoods : previousLikelihoods, likelihoodValues(graph));
            assertEquals(2 * number(a) + number(b), number(sum), 0.0);
            assertEquals(number(sum), logged(sum), 0.0);
            assertEquals(
                    expectedPosterior(integer, loggerOnly, number(a), number(b)), posterior.calculateLogP(), TOLERANCE);
        }
    }

    private static Stream<Arguments> narrowedScalars() {
        return Stream.of(
                Arguments.of("Real", "Normal(mean=1.0,sd=0.5)", "Real", 0.25),
                Arguments.of("PositiveReal", "LogNormal(logMean=0.0,logSd=0.5)", "Real", 0.25),
                Arguments.of("Probability", "Beta(alpha=2.0,beta=3.0)", "Real", 0.25),
                Arguments.of("Integer", "DiscreteUniform(lower=1,upper=9)", "Integer", 2.0),
                Arguments.of("NonNegativeInteger", "Poisson(rate=3.0)", "Integer", 2.0));
    }

    @ParameterizedTest
    @MethodSource("narrowedScalars")
    void scalarListsRetainNarrowedDomainsAndMixWithMatchingLiterals(
            String type, String prior, String resultType, double changedValue) throws Exception {
        String scalar = "%s a ~ %s\n".formatted(type, prior);
        String constant = resultType.equals("Integer") ? "1" : "0.5";
        BEASTState graph = compile(scalar + "%s total = sum(vector=[a,%s,a])".formatted(resultType, constant));
        assertUnchangedSamplingGraph(compile(scalar), graph);
        StateNode a = parameter(graph, "a");
        Object domain = a instanceof RealScalarParam<?> real ? real.getDomain() : ((IntScalarParam<?>) a).getDomain();
        CalculationNode sum = graph.calculationNodes.keySet().iterator().next();
        List<?> elements = sum instanceof RealVectorSum real
                ? ((RealVectorFromScalars) real.vectorInput.get()).valuesInput.get()
                : ((IntVectorFromScalars) ((IntVectorSum) sum).vectorInput.get()).valuesInput.get();
        assertSame(a, elements.getFirst());
        assertSame(a, elements.getLast());
        set(a, changedValue);
        assertEquals(2 * changedValue + Double.parseDouble(constant), number(sum), 0.0);
        assertSame(
                domain, a instanceof RealScalarParam<?> real ? real.getDomain() : ((IntScalarParam<?>) a).getDomain());
        assertEquals(number(sum), logged(sum), 0.0);
    }

    @ParameterizedTest
    @MethodSource("narrowedScalars")
    void indexedDeclarationsFeedLiveScalarLists(String type, String prior, String resultType, double changedValue)
            throws Exception {
        String source = "%s values[i] ~ %s for i in 1:3\n".formatted(type, prior);
        BEASTState graph = compile(source + "%s total = sum(values)".formatted(resultType));
        assertUnchangedSamplingGraph(compile(source), graph);
        assertEquals(3, graph.stateNodes.size());
        CalculationNode sum = graph.calculationNodes.keySet().iterator().next();
        assertEquals(
                graph.stateNodes.keySet().stream()
                        .mapToDouble(SumScalarListTest::number)
                        .sum(),
                number(sum),
                TOLERANCE);
        for (StateNode scalar : graph.stateNodes.keySet()) set(scalar, changedValue);
        assertEquals(3 * changedValue, number(sum), TOLERANCE);
        assertEquals(number(sum), logged(sum), 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void integerListOverflowCanBeReadAgainAfterRestoration(boolean negative) throws Exception {
        String lower = negative ? "-2147483647 - 1" : Integer.toString(Integer.MAX_VALUE - 2);
        int upper = negative ? Integer.MIN_VALUE + 2 : Integer.MAX_VALUE;
        int increment = negative ? -1 : 1;
        BEASTState graph = compile("Integer a ~ DiscreteUniform(lower=%s,upper=%d)\nInteger total = sum([a,%d])"
                .formatted(lower, upper, increment));
        IntScalarParam<?> a = (IntScalarParam<?>) parameter(graph, "a");
        IntVectorSum sum =
                (IntVectorSum) graph.calculationNodes.keySet().iterator().next();
        int expected = negative ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        assertEquals(expected, sum.get());
        CompoundDistribution posterior = posterior(graph);
        State state = state(graph, posterior);
        state.robustlyCalcPosterior(posterior);
        state.store(1);
        a.set(expected);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertThrows(ArithmeticException.class, sum::get);
        assertThrows(ArithmeticException.class, () -> logged(sum));
        state.restore();
        state.restoreCalculationNodes();
        state.setEverythingDirty(false);
        assertEquals(expected, sum.get());
        assertEquals(expected, logged(sum), 0.0);
    }

    @Test
    void integerListChecksOnlyTheFinalSumForOverflow() throws Exception {
        BEASTState graph = compile("""
                Integer a ~ DiscreteUniform(lower=2147483647,upper=2147483647)
                Integer total = sum([a,1,-1])
                """);
        IntVectorSum sum =
                (IntVectorSum) graph.calculationNodes.keySet().iterator().next();
        assertEquals(Integer.MAX_VALUE, sum.get());
    }

    private static void assertUnchangedSamplingGraph(BEASTState baseline, BEASTState graph) {
        assertEquals(baseline.stateNodes.size(), graph.stateNodes.size());
        assertEquals(baseline.priorDistributions.size(), graph.priorDistributions.size());
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());
        for (StateNode node : graph.stateNodes.keySet()) {
            StateNode original = parameter(baseline, node.getID());
            assertEquals(original.getClass(), node.getClass());
            assertEquals(number(original), number(node), 0.0);
            assertEquals(
                    baseline.priorDistributions.get(original).getClass(),
                    graph.priorDistributions.get(node).getClass());
        }
    }

    private static StateNode parameter(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static double number(Object value) {
        return value instanceof RealScalar<?> real ? real.get() : ((IntScalar<?>) value).get();
    }

    private static void set(StateNode value, double proposal) {
        if (value instanceof RealScalarParam<?> real) real.set(proposal);
        else ((IntScalarParam<?>) value).set((int) proposal);
    }

    private static CompoundDistribution posterior(BEASTState graph) {
        List<Distribution> factors = new ArrayList<>(graph.priorDistributions.values());
        factors.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        return posterior;
    }

    private static State state(BEASTState graph, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static List<Double> likelihoodValues(BEASTState graph) {
        return graph.likelihoodDistributions.stream()
                .map(Distribution::getCurrentLogP)
                .toList();
    }

    private static double expectedPosterior(boolean integer, boolean loggerOnly, double a, double b) {
        double prior = -2 * Math.log(integer ? 9.0 : 8.0);
        if (loggerOnly) return prior;
        double sum = 2 * a + b;
        return integer
                ? prior - 2 * Math.log(sum + 1)
                : prior - Math.log(2 * Math.PI) - Math.log(2) - Math.pow(4 - sum, 2) / 2 - Math.pow(3 - sum, 2) / 8;
    }

    private static double logged(CalculationNode value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ((Loggable) value).log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }
}
