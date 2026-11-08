package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;
import static tiles.functions.SumTileTest.compile;

import beast.base.core.Loggable;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.typeresolver.TypeError;
import tiling.ExpValue;
import tiling.IntVectorFromScalars;
import tiling.IntVectorSum;
import tiling.RealVectorFromScalars;
import tiling.RealVectorSum;

class MixedScalarSumTest {
    private static final double TOLERANCE = 1e-10;
    private static final String REAL_SOURCE = "Real x ~ Normal(mean=0.0,sd=2.0)\n";
    private static final String LOGGER = """
            mcmc {
                Logger trace = fileLogger(logEvery=1,file="mixed-sum.log",parameters=[total])
            }
            """;

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void namedAndInlineListsSupportFixedAndSampledDerivedScalars(boolean sampled, boolean named) throws Exception {
        String source = sampled ? REAL_SOURCE : "Real x = 0.25\n";
        BEASTState graph = compile(source
                + (named
                        ? "Vector<Real> values = [x,exp(x)]\nReal total = sum(vector=values)\n"
                        : "Real total = sum([x,exp(x)])\n")
                + LOGGER);
        assertSamplingUnchanged(compile(source), graph);
        RealVectorSum total = total(graph, RealVectorSum.class);
        double x = sampled ? number(parameter(graph, "x")) : 0.25;
        assertEquals(x + Math.exp(x), total.get(), TOLERANCE);
        if (sampled) {
            RealScalarParam<?> parameter = (RealScalarParam<?>) parameter(graph, "x");
            RealVectorFromScalars values = assertInstanceOf(RealVectorFromScalars.class, total.vectorInput.get());
            assertSame(parameter, values.valuesInput.get().getFirst());
            assertSame(
                    parameter,
                    assertInstanceOf(ExpValue.class, values.valuesInput.get().getLast())
                            .xInput
                            .get());
            parameter.set(0.75);
            assertEquals(0.75 + Math.exp(0.75), total.get(), TOLERANCE);
        }
        assertEquals(total.get(), logged(total), 0.0);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void sharedMixedListTracksPosteriorCachesAndLoggerOnlyRestoration(boolean loggerOnly, boolean acceptFirst)
            throws Exception {
        BEASTState graph = compile(REAL_SOURCE + """
                Vector<Real> values = [x,exp(x),x]
                Real total = sum(values)
                """
                + (loggerOnly ? "" : "Real y ~ Normal(mean=total,sd=1.0) observed as 2.0\n") + LOGGER);
        assertSamplingUnchanged(compile(REAL_SOURCE), graph);
        RealScalarParam<?> x = (RealScalarParam<?>) parameter(graph, "x");
        RealVectorSum total = total(graph, RealVectorSum.class);
        RealVectorFromScalars values = assertInstanceOf(RealVectorFromScalars.class, total.vectorInput.get());
        ExpValue derived =
                assertInstanceOf(ExpValue.class, values.valuesInput.get().get(1));
        assertEquals(List.of(x, derived, x), values.valuesInput.get());
        assertSame(x, derived.xInput.get());
        assertTrue(x.getOutputs().contains(values));
        assertTrue(x.getOutputs().contains(derived));
        assertTrue(derived.getOutputs().contains(values));
        assertTrue(values.getOutputs().contains(total));
        assertEquals(2, graph.calculationNodes.size());
        if (!loggerOnly) {
            Normal likelihood = assertInstanceOf(Normal.class, graph.likelihoodDistributions.getFirst());
            assertSame(total, likelihood.meanInput.get());
            assertTrue(total.getOutputs().contains(likelihood));
        }
        List<Distribution> factors = new ArrayList<>(graph.priorDistributions.values());
        factors.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(expectedPosterior(x.get(), loggerOnly), state.robustlyCalcPosterior(posterior), TOLERANCE);
        assertEquals(mixedSum(x.get()), total.get(), TOLERANCE);
        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        double[] proposals = {0.25, -0.5, 0.75};
        for (int step = 0; step < decisions.length; step++) {
            double previousX = x.get();
            double previousPosterior = posterior.getCurrentLogP();
            List<Double> previousFactors = cached(factors);
            state.store(step + 1);
            x.set(proposals[step]);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            for (var node : List.of(derived, values, total)) {
                assertEquals(!loggerOnly, state.getCurrentCalculationNodes().contains(node));
            }
            double proposed = posterior.calculateLogP();
            assertEquals(expectedPosterior(x.get(), loggerOnly), proposed, TOLERANCE);
            assertEquals(mixedSum(x.get()), total.get(), TOLERANCE);
            assertEquals(total.get(), logged(total), 0.0);
            List<Double> proposedFactors = cached(factors);
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            // Read all native caches before explicitly recalculating the restored posterior.
            assertEquals(decisions[step] ? proposed : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(decisions[step] ? proposedFactors : previousFactors, cached(factors));
            assertEquals(decisions[step] ? proposals[step] : previousX, x.get(), 0.0);
            assertEquals(mixedSum(x.get()), total.get(), TOLERANCE);
            assertEquals(total.get(), logged(total), 0.0);
            assertEquals(expectedPosterior(x.get(), loggerOnly), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void realListsRetainDifferentSampledDomainsAndTheirOriginalParameters() throws Exception {
        String source = """
                Real x ~ Normal(mean=0.0,sd=1.0)
                PositiveReal rate ~ LogNormal(logMean=0.0,logSd=0.5)
                Probability probability ~ Beta(alpha=2.0,beta=3.0)
                """;
        BEASTState graph = compile(source + "Real total = sum([x,rate,probability,exp(x)])\n" + LOGGER);
        assertSamplingUnchanged(compile(source), graph);
        RealScalarParam<?> x = (RealScalarParam<?>) parameter(graph, "x");
        RealScalarParam<?> rate = (RealScalarParam<?>) parameter(graph, "rate");
        RealScalarParam<?> probability = (RealScalarParam<?>) parameter(graph, "probability");
        RealVectorSum total = total(graph, RealVectorSum.class);
        RealVectorFromScalars values = assertInstanceOf(RealVectorFromScalars.class, total.vectorInput.get());
        assertEquals(List.of(x, rate, probability), values.valuesInput.get().subList(0, 3));
        assertSame(Real.INSTANCE, x.getDomain());
        assertSame(PositiveReal.INSTANCE, rate.getDomain());
        assertSame(UnitInterval.INSTANCE, probability.getDomain());
        x.set(-0.5);
        rate.set(2.0);
        probability.set(0.25);
        assertEquals(1.75 + Math.exp(-0.5), total.get(), TOLERANCE);
        assertEquals(total.get(), logged(total), 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void integerListsMixNarrowedDomainsWithDerivedSums(boolean named) throws Exception {
        String source = """
                PositiveInteger category ~ Categorical(probabilities=[0.2,0.3,0.5])
                Integer offset ~ DiscreteUniform(lower=1,upper=5)
                """;
        BEASTState graph = compile(source + "Integer inner = sum([category,offset])\n"
                + (named
                        ? "Vector<Integer> values = [category,offset,inner,category]\nInteger total = sum(values)\n"
                        : "Integer total = sum([category,offset,inner,category])\n")
                + "Integer y ~ DiscreteUniform(lower=0,upper=total) observed as 1\n" + LOGGER);
        assertSamplingUnchanged(compile(source), graph);
        IntScalarParam<?> category = (IntScalarParam<?>) parameter(graph, "category");
        IntScalarParam<?> offset = (IntScalarParam<?>) parameter(graph, "offset");
        IntVectorSum total = total(graph, IntVectorSum.class);
        IntVectorFromScalars values = assertInstanceOf(IntVectorFromScalars.class, total.vectorInput.get());
        IntVectorSum inner =
                assertInstanceOf(IntVectorSum.class, values.valuesInput.get().get(2));
        assertEquals(List.of(category, offset, inner, category), values.valuesInput.get());
        assertSame(PositiveInt.INSTANCE, category.getDomain());
        assertSame(Int.INSTANCE, offset.getDomain());
        assertSame(Int.INSTANCE, total.getDomain());
        category.set(2);
        offset.set(4);
        assertEquals(6, inner.get());
        assertEquals(14, total.get());
        IntUniform likelihood = assertInstanceOf(IntUniform.class, graph.likelihoodDistributions.getFirst());
        assertSame(total, likelihood.upperInput.get());
        assertEquals(-Math.log(15), likelihood.calculateLogP(), TOLERANCE);
        assertEquals(14.0, logged(total), 0.0);

        List<Distribution> factors =
                List.of(graph.priorDistributions.get(category), graph.priorDistributions.get(offset), likelihood);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(
                expectedIntegerPosterior(category.get(), offset.get()),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        double[] probabilities = {0.2, 0.3, 0.5};
        IntScalarParam<?>[] changed = {category, offset, category, offset};
        int[] proposals = {3, 2, 1, 5};
        boolean[] decisions = {true, false, false, true};
        for (int step = 0; step < decisions.length; step++) {
            int previousCategory = category.get();
            int previousOffset = offset.get();
            double previousPosterior = posterior.getCurrentLogP();
            List<Double> previousFactors = cached(factors);
            state.store(step + 1);
            changed[step].set(proposals[step]);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertTrue(state.getCurrentCalculationNodes().contains(inner));
            assertTrue(state.getCurrentCalculationNodes().contains(values));
            assertTrue(state.getCurrentCalculationNodes().contains(total));
            double proposed = posterior.calculateLogP();
            assertEquals(expectedIntegerPosterior(category.get(), offset.get()), proposed, TOLERANCE);
            assertEquals(
                    Math.log(probabilities[category.get() - 1]), factors.get(0).getCurrentLogP(), TOLERANCE);
            assertEquals(-Math.log(5), factors.get(1).getCurrentLogP(), TOLERANCE);
            assertEquals(category.get() + offset.get(), inner.get());
            assertEquals(3 * category.get() + 2 * offset.get(), total.get());
            assertEquals(-Math.log(total.get() + 1), likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(total.get(), logged(total), 0.0);
            List<Double> proposedFactors = cached(factors);
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            // Check both priors, the likelihood, and the posterior before recalculating any density.
            assertEquals(decisions[step] ? proposed : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(decisions[step] ? proposedFactors : previousFactors, cached(factors));
            assertEquals(
                    decisions[step] && changed[step] == category ? proposals[step] : previousCategory, category.get());
            assertEquals(decisions[step] && changed[step] == offset ? proposals[step] : previousOffset, offset.get());
            assertEquals(category.get() + offset.get(), inner.get());
            assertEquals(3 * category.get() + 2 * offset.get(), total.get());
            assertEquals(total.get(), logged(total), 0.0);
            assertEquals(expectedIntegerPosterior(category.get(), offset.get()), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void mixedListsComposeWithRepeatNestedListsAndIndexing() throws Exception {
        BEASTState graph = compile(REAL_SOURCE + """
                Vector<Real> values = [x,exp(x)]
                Vector<Vector<Real>> rows = repeat(value=values,num=2)
                Real selected = rows[2][2]
                Real total = sum([sum(rows[1]),sum(repeat(value=selected,num=2)),x])
                """ + LOGGER);
        assertSamplingUnchanged(compile(REAL_SOURCE), graph);
        RealScalarParam<?> x = (RealScalarParam<?>) parameter(graph, "x");
        RealVectorSum total = total(graph, RealVectorSum.class);
        for (double value : new double[] {0.0, 0.5, -0.25}) {
            x.set(value);
            assertEquals(2 * value + 3 * Math.exp(value), total.get(), TOLERANCE);
        }
    }

    @Test
    void mixedScalarSupportDoesNotImplicitlyPromoteSampledIntegersToReals() {
        assertThrows(TypeError.class, () -> compile("""
                Integer category ~ DiscreteUniform(lower=1,upper=3)
                Real x ~ Normal(mean=0.0,sd=1.0)
                Real total = sum([category,x])
                """));
    }

    private static void assertSamplingUnchanged(BEASTState baseline, BEASTState graph) {
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

    private static <T> T total(BEASTState graph, Class<T> type) {
        return assertInstanceOf(
                type, graph.fileLoggerSpecs.getFirst().parameters().getFirst());
    }

    private static double number(Object value) {
        return value instanceof RealScalar<?> real ? real.get() : ((IntScalar<?>) value).get();
    }

    private static List<Double> cached(List<Distribution> factors) {
        return factors.stream().map(Distribution::getCurrentLogP).toList();
    }

    private static double mixedSum(double x) {
        return 2 * x + Math.exp(x);
    }

    private static double expectedPosterior(double x, boolean loggerOnly) {
        double prior = -Math.log(2 * Math.sqrt(2 * Math.PI)) - x * x / 8;
        double residual = 2 - mixedSum(x);
        return loggerOnly ? prior : prior - 0.5 * Math.log(2 * Math.PI) - residual * residual / 2;
    }

    private static double expectedIntegerPosterior(int category, int offset) {
        double[] probabilities = {0.2, 0.3, 0.5};
        return Math.log(probabilities[category - 1]) - Math.log(5) - Math.log(3 * category + 2 * offset + 1);
    }

    private static double logged(Loggable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }
}
