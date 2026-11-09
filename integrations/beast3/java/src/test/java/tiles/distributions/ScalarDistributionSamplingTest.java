package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Input;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.distribution.Poisson;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import operators.IntScalarRandomWalkOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class ScalarDistributionSamplingTest {
    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy", "Poisson"})
    void preservesNativeDistributionParametersDomainAndInitialValue(String family) throws Exception {
        BEASTState graph = build(script(family));
        StateNode x = node(graph, "x");
        Distribution prior = graph.priorDistributions.get(x);
        assertEquals(family.equals("Normal") ? 0.1 : 0.0, value(x));
        switch (family) {
            case "Normal" -> {
                Normal normal = assertInstanceOf(Normal.class, prior);
                assertSame(x, normal.paramInput.get());
                assertEquals(-0.4, normal.meanInput.get().get());
                assertEquals(1.3, normal.sdInput.get().get());
                assertSame(Real.INSTANCE, ((RealScalarParam<?>) x).getDomain());
            }
            case "Cauchy" -> {
                Cauchy cauchy = assertInstanceOf(Cauchy.class, prior);
                assertSame(x, cauchy.paramInput.get());
                assertEquals(0.2, cauchy.locationInput.get().get());
                assertEquals(1.5, cauchy.scaleInput.get().get());
                assertSame(Real.INSTANCE, ((RealScalarParam<?>) x).getDomain());
            }
            case "Poisson" -> {
                Poisson poisson = assertInstanceOf(Poisson.class, prior);
                assertSame(x, poisson.paramInput.get());
                assertEquals(2.3, poisson.lambdaInput.get().get());
                assertSame(NonNegativeInt.INSTANCE, ((IntScalarParam<?>) x).getDomain());
            }
            default -> throw new AssertionError(family);
        }
        assertEquals(priorLogDensity(family, value(x)), prior.calculateLogP(), 1e-12);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        Operator operator = operator(graph, x);
        if (family.equals("Poisson")) {
            IntScalarRandomWalkOperator walk = assertInstanceOf(IntScalarRandomWalkOperator.class, operator);
            assertSame(x, walk.parameterInput.get());
            assertEquals(10.0, walk.m_pWeight.get());
        } else {
            RealRandomWalkOperator walk = assertInstanceOf(RealRandomWalkOperator.class, operator);
            assertSame(x, walk.scalarInput.get());
            assertNull(walk.parameterInput.get());
            assertEquals(5.0, walk.m_pWeight.get());
            assertEquals(1.0, walk.getCoercableParameterValue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy"})
    void realProposalsCanLeaveZeroAndCrossTheStartingSign(String family) throws Exception {
        BEASTState graph = build(script(family));
        RealScalarParam<?> x = (RealScalarParam<?>) node(graph, "x");
        Operator operator = operator(graph, x);
        double initial = x.get();
        boolean negative = false;
        boolean positive = false;
        Randomizer.setSeed(2026102801L);
        for (int attempt = 0; attempt < 100; attempt++) {
            // Independently exercise proposals from the unchanged native starting value.
            x.set(initial);
            assertTrue(Double.isFinite(operator.proposal()));
            assertTrue(Double.isFinite(x.get()));
            negative |= x.get() < 0.0;
            positive |= x.get() > 0.0;
        }
        assertTrue(negative && positive, "A real-valued prior must not be confined to one sign or to zero.");
    }

    @Test
    void poissonProposalsMoveBetweenCountsAndRejectAtTheZeroBoundary() throws Exception {
        BEASTState graph = build(script("Poisson"));
        IntScalarParam<?> x = (IntScalarParam<?>) node(graph, "x");
        Operator operator = operator(graph, x);
        boolean movedUp = false;
        boolean rejected = false;
        Randomizer.setSeed(2026102802L);
        for (int attempt = 0; attempt < 100; attempt++) {
            x.set(0);
            double logHastings = operator.proposal();
            if (logHastings == Double.NEGATIVE_INFINITY) {
                rejected = true;
                assertEquals(0, x.get(), "A rejected proposal must not mutate the parameter.");
            } else {
                assertEquals(0.0, logHastings);
                assertEquals(1, x.get());
                movedUp = true;
            }
        }
        boolean returnedToZero = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            x.set(1);
            double logHastings = operator.proposal();
            assertTrue(x.get() >= 0);
            if (logHastings == Double.NEGATIVE_INFINITY) {
                assertEquals(1, x.get(), "A rejected self proposal must leave the parameter unchanged.");
            } else {
                assertEquals(0.0, logHastings);
                assertTrue(x.get() == 0 || x.get() == 2);
                returnedToZero |= x.get() == 0;
            }
        }
        assertTrue(movedUp && returnedToZero && rejected);
        assertSame(NonNegativeInt.INSTANCE, x.getDomain());
        assertEquals(0, x.getLower());
        assertEquals(Integer.MAX_VALUE, x.getUpper());
    }

    private static Stream<Arguments> acceptanceOrders() {
        return Stream.of("Normal", "Cauchy", "Poisson")
                .flatMap(family -> Stream.of(Arguments.of(family, false), Arguments.of(family, true)));
    }

    @ParameterizedTest
    @MethodSource("acceptanceOrders")
    void actualProposalsPreserveAcceptedAndRestoreRejectedStates(String family, boolean acceptFirst) throws Exception {
        BEASTState graph = build(script(family));
        StateNode x = node(graph, "x");
        Distribution prior = graph.priorDistributions.get(x);
        GaussianObservation likelihood = new GaussianObservation();
        likelihood.initByName("mean", x);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, likelihood));
        Operator operator = operator(graph, x);
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertTrue(Double.isFinite(state.robustlyCalcPosterior(posterior)));
        Randomizer.setSeed(2026102803L);

        // Force both decisions to check storage separately from the MH acceptance probability.
        for (int step = 0; step < 2; step++) {
            boolean accept = step == 0 ? acceptFirst : !acceptFirst;
            double previousValue = value(x);
            double previousLogP = posterior.getCurrentLogP();
            boolean moved = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                state.store(step * 100L + attempt);
                double logHastings = operator.proposal();
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                if (Double.isFinite(logHastings) && value(x) != previousValue) {
                    moved = true;
                    break;
                }
                state.restore();
                state.restoreCalculationNodes();
                state.setEverythingDirty(false);
            }
            assertTrue(moved, "The selected operator must actually change this state node.");
            double proposedValue = value(x);
            double proposedLogP = posterior.calculateLogP();
            double expectedLogP = priorLogDensity(family, proposedValue) + observationLogDensity(proposedValue);
            assertEquals(expectedLogP, proposedLogP, 1e-12);
            assertNotEquals(previousLogP, proposedLogP);
            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(accept ? proposedValue : previousValue, value(x));
            assertEquals(accept ? proposedLogP : previousLogP, posterior.getCurrentLogP(), 1e-12);
            assertEquals(priorLogDensity(family, value(x)), prior.getCurrentLogP(), 1e-12);
            assertEquals(observationLogDensity(value(x)), likelihood.getCurrentLogP(), 1e-12);
            assertEquals(
                    priorLogDensity(family, value(x)) + observationLogDensity(value(x)),
                    posterior.calculateLogP(),
                    1e-12);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Normal", "Cauchy", "Poisson"})
    void observedValuesDoNotReceiveSamplingOperators(String family) throws Exception {
        BEASTState graph = build(script(family) + " observed as " + (family.equals("Poisson") ? "3" : "3.0"));
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        Distribution likelihood = graph.likelihoodDistributions.getFirst();
        double observed =
                switch (family) {
                    case "Normal" ->
                        assertInstanceOf(Normal.class, likelihood)
                                .paramInput
                                .get()
                                .get();
                    case "Cauchy" ->
                        assertInstanceOf(Cauchy.class, likelihood)
                                .paramInput
                                .get()
                                .get();
                    case "Poisson" ->
                        assertInstanceOf(Poisson.class, likelihood)
                                .paramInput
                                .get()
                                .get();
                    default -> throw new AssertionError(family);
                };
        assertEquals(3.0, observed);
        assertEquals(priorLogDensity(family, observed), likelihood.calculateLogP(), 1e-12);
    }

    private static Stream<Arguments> positiveFamilies() {
        return Stream.of(
                Arguments.of("PositiveReal", "Gamma(shape=2.0, rate=1.0)", PositiveReal.INSTANCE, 1.0),
                Arguments.of("Probability", "Beta(alpha=2.0, beta=3.0)", UnitInterval.INSTANCE, 0.5),
                Arguments.of("NonNegativeReal", "Exponential(rate=2.0)", NonNegativeReal.INSTANCE, 1.0),
                Arguments.of("PositiveReal", "LogNormal(logMean=0.3, logSd=0.4)", PositiveReal.INSTANCE, Math.exp(0.3)),
                Arguments.of(
                        "PositiveReal",
                        "LogNormal(mean=2.0, logSd=0.4)",
                        PositiveReal.INSTANCE,
                        Math.exp(Math.log(2.0) - 0.5 * 0.4 * 0.4)));
    }

    @ParameterizedTest
    @MethodSource("positiveFamilies")
    void positiveFamiliesKeepScaleOperatorsDomainsAndInitialization(
            String declaredType, String distribution, Real domain, double initial) throws Exception {
        BEASTState graph = build(declaredType + " x ~ " + distribution);
        RealScalarParam<?> x = assertInstanceOf(RealScalarParam.class, node(graph, "x"));
        assertSame(domain, x.getDomain());
        assertEquals(initial, x.get(), 1e-12);
        ScaleOperator scale = assertInstanceOf(ScaleOperator.class, operator(graph, x));
        assertSame(x, scale.parameterInput.get());
        assertEquals(5.0, scale.m_pWeight.get());
    }

    private static String script(String family) {
        return switch (family) {
            case "Normal" -> "Real x ~ Normal(mean=-0.4, sd=1.3)";
            case "Cauchy" -> "Real x ~ Cauchy(location=0.2, scale=1.5)";
            case "Poisson" -> "NonNegativeInteger x ~ Poisson(rate=2.3)";
            default -> throw new AssertionError(family);
        };
    }

    private static double priorLogDensity(String family, double value) {
        return switch (family) {
            case "Normal" -> normalLogDensity(value, -0.4, 1.3);
            case "Cauchy" -> cauchyLogDensity(value, 0.2, 1.5);
            case "Poisson" -> {
                double logFactorial = 0.0;
                for (int i = 2; i <= (int) value; i++) logFactorial += Math.log(i);
                yield value * Math.log(2.3) - 2.3 - logFactorial;
            }
            default -> throw new AssertionError(family);
        };
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double standardized = (value - mean) / sd;
        return -Math.log(sd) - 0.5 * Math.log(2.0 * Math.PI) - 0.5 * standardized * standardized;
    }

    private static double cauchyLogDensity(double value, double location, double scale) {
        double standardized = (value - location) / scale;
        return -Math.log(Math.PI * scale) - Math.log1p(standardized * standardized);
    }

    private static double observationLogDensity(double mean) {
        return normalLogDensity(4.0, mean, 1.0);
    }

    /** A fixed observation with a sampled real or integer mean, to exercise dependent caches. */
    public static class GaussianObservation extends Distribution {
        public final Input<StateNode> meanInput = new Input<>("mean", "Sampled mean", Input.Validate.REQUIRED);

        @Override
        public double calculateLogP() {
            return logP = observationLogDensity(value(meanInput.get()));
        }

        @Override
        public List<String> getArguments() {
            return List.of();
        }

        @Override
        public List<String> getConditions() {
            return List.of(meanInput.get().getID());
        }

        @Override
        public void sample(State state, Random random) {
            throw new UnsupportedOperationException("The test observation is fixed.");
        }
    }

    private static double value(StateNode node) {
        return node instanceof RealScalarParam<?> real ? real.get() : ((IntScalarParam<?>) node).get();
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static Operator operator(BEASTState graph, StateNode node) {
        List<Operator> operators = graph.operators.stream()
                .filter(operator -> operator.listStateNodes().contains(node))
                .toList();
        assertEquals(1, operators.size(), "Each sampled scalar needs one correctly bound default operator.");
        return operators.getFirst();
    }

    private static BEASTState build(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        var graph = evaluator.applyBestTiling(new BEASTState("scalar-distribution-sampling"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
