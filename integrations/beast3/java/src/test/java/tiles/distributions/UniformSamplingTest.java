package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Input;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import operators.IntScalarUniformOperator;
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

class UniformSamplingTest {
    private static Stream<Arguments> realIntervals() {
        return Stream.of(
                Arguments.of(1.0, 10.0, 5.5),
                Arguments.of(-10.0, -2.0, -6.0),
                Arguments.of(-1.0, 1.0, 0.0),
                Arguments.of(1.0, 1.000000001, 1.0000000005),
                Arguments.of(8.0e307, 1.2e308, 1.0e308));
    }

    private static Stream<Arguments> integerIntervals() {
        return Stream.of(
                Arguments.of(1, 10, 5),
                Arguments.of(-10, -2, -6),
                Arguments.of(-3, 3, 0),
                Arguments.of(2147483637, 2147483647, 2147483642),
                Arguments.of(-2147483647, 2147483647, 0),
                Arguments.of(7, 7, 7));
    }

    @ParameterizedTest
    @MethodSource("realIntervals")
    void realInitializationPreservesTheNativePriorAndDomain(double lower, double upper, double expected)
            throws Exception {
        BEASTState graph =
                build("Real x ~ Uniform(lower=" + realLiteral(lower) + ", upper=" + realLiteral(upper) + ")");
        RealScalarParam<?> x = real(graph, "x");
        Uniform prior = assertInstanceOf(Uniform.class, graph.priorDistributions.get(x));
        assertEquals(expected, x.get(), Math.abs(expected) * 1e-15);
        assertSame(Real.INSTANCE, x.getDomain());
        assertSame(x, prior.paramInput.get());
        assertEquals(lower, prior.lowerInput.get().get());
        assertEquals(upper, prior.upperInput.get().get());
        assertEquals(-Math.log(upper - lower), prior.calculateLogP(), 1e-12);
        Operator operator = operator(graph, x);
        assertInstanceOf(RealRandomWalkOperator.class, operator);
        Randomizer.setSeed(6219);
        boolean moved = false;
        for (int i = 0; i < 100; i++) {
            double previous = x.get();
            operator.proposal();
            moved |= x.get() != previous;
            assertTrue(x.get() >= lower && x.get() <= upper);
        }
        assertTrue(moved, "The initial proposal window must work for this interval's numerical scale.");
        x.set(lower);
        assertEquals(-Math.log(upper - lower), prior.calculateLogP(), 1e-12);
        x.set(upper);
        assertEquals(-Math.log(upper - lower), prior.calculateLogP(), 1e-12);
    }

    @ParameterizedTest
    @MethodSource("integerIntervals")
    void integerInitializationPreservesTheNativePriorAndDomain(int lower, int upper, int expected) throws Exception {
        BEASTState graph = build("Integer x ~ DiscreteUniform(lower=" + lower + ", upper=" + upper + ")");
        IntScalarParam<?> x = integer(graph, "x");
        IntUniform prior = assertInstanceOf(IntUniform.class, graph.priorDistributions.get(x));
        assertEquals(expected, x.get());
        assertSame(Int.INSTANCE, x.getDomain());
        assertSame(x, prior.paramInput.get());
        assertEquals(lower, prior.lowerInput.get().get());
        assertEquals(upper, prior.upperInput.get().get());
        double expectedLogP = -Math.log((long) upper - lower + 1);
        assertEquals(expectedLogP, prior.calculateLogP(), 1e-12);
        Operator operator = operator(graph, x);
        assertInstanceOf(IntScalarUniformOperator.class, operator);
        Randomizer.setSeed(4921);
        for (int i = 0; i < 100; i++) {
            assertEquals(lower == upper ? Double.NEGATIVE_INFINITY : 0.0, operator.proposal());
            assertTrue(x.get() >= lower && x.get() <= upper);
            assertEquals(expectedLogP, prior.calculateLogP(), 1e-12);
        }
    }

    @Test
    void realOperatorLeavesZeroAndExploresBothSigns() throws Exception {
        BEASTState graph = build("Real x ~ Uniform(lower=-1.0, upper=1.0)");
        RealScalarParam<?> x = real(graph, "x");
        Operator operator = operator(graph, x);
        assertEquals(0.0, x.get());
        Randomizer.setSeed(8013);
        boolean negative = false;
        boolean positive = false;
        boolean rejected = false;
        for (int i = 0; i < 300; i++) {
            double previous = x.get();
            double logHastings = operator.proposal();
            if (logHastings == Double.NEGATIVE_INFINITY) {
                rejected = true;
                assertEquals(previous, x.get(), "An out-of-bounds proposal must not move the parameter.");
            } else {
                assertEquals(0.0, logHastings);
            }
            assertTrue(x.get() >= -1.0 && x.get() <= 1.0);
            negative |= x.get() < 0.0;
            positive |= x.get() > 0.0;
        }
        assertTrue(negative && positive, "Scaling alone would remain at zero or preserve its starting sign.");
        assertTrue(rejected, "Exercise the out-of-bounds rejection path as well as successful proposals.");
    }

    @Test
    void integerOperatorCanVisitEveryValueIncludingBothEndpoints() throws Exception {
        BEASTState graph = build("Integer x ~ DiscreteUniform(lower=-3, upper=3)");
        IntScalarParam<?> x = integer(graph, "x");
        Operator operator = operator(graph, x);
        Set<Integer> visited = new HashSet<>();
        Randomizer.setSeed(3199);
        for (int i = 0; i < 200; i++) {
            assertEquals(0.0, operator.proposal());
            visited.add(x.get());
        }
        assertEquals(Set.of(-3, -2, -1, 0, 1, 2, 3), visited);
    }

    @Test
    void integerMinimumCanBeProposedWithoutArithmeticOverflow() throws Exception {
        // Supply MIN_VALUE programmatically: the script lexer parses the positive part of a
        // negative integer literal separately, and 2147483648 is outside its int range.
        BEASTState graph = build("Integer x ~ DiscreteUniform(lower=-2147483647, upper=-2147483647)");
        IntScalarParam<?> x = integer(graph, "x");
        IntUniform prior = assertInstanceOf(IntUniform.class, graph.priorDistributions.get(x));
        graph.setInput(prior, prior.lowerInput, new IntScalarParam<>(Integer.MIN_VALUE, Int.INSTANCE));
        prior.initAndValidate();
        Operator operator = operator(graph, x);
        Set<Integer> visited = new HashSet<>();
        Randomizer.setSeed(6137);
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, operator.proposal());
            visited.add(x.get());
        }
        assertEquals(Set.of(Integer.MIN_VALUE, Integer.MIN_VALUE + 1), visited);
    }

    private static Stream<Arguments> acceptanceOrders() {
        return Stream.of(
                Arguments.of(false, false), Arguments.of(false, true),
                Arguments.of(true, false), Arguments.of(true, true));
    }

    @ParameterizedTest
    @MethodSource("acceptanceOrders")
    void actualOperatorsPreserveAcceptedAndRestoreRejectedStates(boolean discrete, boolean acceptFirst)
            throws Exception {
        BEASTState graph = build(
                discrete
                        ? "Integer x ~ DiscreteUniform(lower=-3, upper=3)"
                        : "Real x ~ Uniform(lower=-1.0, upper=1.0)");
        StateNode x = node(graph, "x");
        Distribution prior = graph.priorDistributions.get(x);
        GaussianObservation likelihood = new GaussianObservation();
        likelihood.initByName("mean", x);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, likelihood));
        Operator operator = operator(graph, x);
        State state = state(graph, posterior);
        double initialLogP = state.robustlyCalcPosterior(posterior);
        assertTrue(Double.isFinite(initialLogP));
        Randomizer.setSeed(8203);

        // Decisions are forced here to test BEAST's storage lifecycle independently of MH acceptance.
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
                if (logHastings == 0.0 && value(x) != previousValue) {
                    moved = true;
                    break;
                }
                state.restore();
                state.restoreCalculationNodes();
                state.setEverythingDirty(false);
            }
            assertTrue(moved, "The operator must actually change the sampled parameter.");
            double proposedValue = value(x);
            double proposedLogP = posterior.calculateLogP();
            double expectedLogP = -Math.log(discrete ? 7.0 : 2.0) + gaussianLogDensity(proposedValue);
            assertEquals(expectedLogP, proposedLogP, 1e-12);
            assertNotEquals(previousLogP, proposedLogP, "A nonconstant likelihood exposes stale calculation caches.");
            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(accept ? proposedValue : previousValue, value(x));
            assertEquals(accept ? proposedLogP : previousLogP, posterior.getCurrentLogP(), 1e-12);
            assertEquals(gaussianLogDensity(value(x)), likelihood.getCurrentLogP(), 1e-12);
            assertEquals(
                    -Math.log(discrete ? 7.0 : 2.0) + gaussianLogDensity(value(x)), posterior.calculateLogP(), 1e-12);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stochasticBoundsRemainConnectedAndTheirPriorUpdatesSurviveRollback(boolean discrete) throws Exception {
        BEASTState graph = build(
                discrete
                        ? "Integer upper ~ DiscreteUniform(lower=8, upper=12)\nInteger x ~ DiscreteUniform(lower=1, upper=upper)"
                        : "Real upper ~ Uniform(lower=8.0, upper=12.0)\nReal x ~ Uniform(lower=1.0, upper=upper)");
        StateNode x = node(graph, "x");
        StateNode upper = node(graph, "upper");
        Distribution prior = graph.priorDistributions.get(x);
        if (discrete) {
            assertSame(
                    upper, assertInstanceOf(IntUniform.class, prior).upperInput.get());
        } else {
            assertSame(upper, assertInstanceOf(Uniform.class, prior).upperInput.get());
        }
        double initialX = value(x);
        State state = state(graph, prior);
        state.robustlyCalcPosterior(prior);
        for (boolean accept : new boolean[] {true, false}) {
            double previousUpper = value(upper);
            double previousLogP = prior.getCurrentLogP();
            double proposedUpper = accept ? 12.0 : 8.0;
            state.store(accept ? 1 : 2);
            if (discrete) {
                integer(graph, "upper").set((int) proposedUpper);
            } else {
                real(graph, "upper").set(proposedUpper);
            }
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposedLogP = prior.calculateLogP();
            assertEquals(-Math.log(discrete ? proposedUpper : proposedUpper - 1.0), proposedLogP, 1e-12);
            assertNotEquals(previousLogP, proposedLogP);
            assertEquals(initialX, value(x), "Changing a bound must not reinitialize its sampled child.");
            if (accept) state.acceptCalculationNodes();
            else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(accept ? proposedUpper : previousUpper, value(upper));
            assertEquals(accept ? proposedLogP : previousLogP, prior.getCurrentLogP(), 1e-12);
            assertEquals(-Math.log(discrete ? value(upper) : value(upper) - 1.0), prior.calculateLogP(), 1e-12);
        }
    }

    @Test
    void integerOperatorReadsExpandedAndShrunkenBoundsOnEveryProposal() throws Exception {
        BEASTState graph = build("""
                Integer upper ~ DiscreteUniform(lower=4, upper=16)
                Integer x ~ DiscreteUniform(lower=1, upper=upper)
                """);
        IntScalarParam<?> upper = integer(graph, "upper");
        IntScalarParam<?> x = integer(graph, "x");
        Operator operator = operator(graph, x);
        double initialX = x.get();
        upper.set(16);
        assertEquals(initialX, x.get());
        Randomizer.setSeed(7921);
        boolean reachedExpandedRange = false;
        for (int i = 0; i < 200; i++) {
            assertEquals(0.0, operator.proposal());
            reachedExpandedRange |= x.get() > 10;
        }
        assertTrue(reachedExpandedRange, "The operator must not retain its initial upper bound of 10.");
        x.set(1);
        upper.set(4);
        for (int i = 0; i < 100; i++) {
            assertEquals(0.0, operator.proposal());
            assertTrue(x.get() >= 1 && x.get() <= 4);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observedValuesHaveNeitherDefaultReplacementNorSamplingOperators(boolean discrete) throws Exception {
        BEASTState graph = build(
                discrete
                        ? "Integer x ~ DiscreteUniform(lower=1, upper=10) observed as 9"
                        : "Real x ~ Uniform(lower=1.0, upper=10.0) observed as 9.0");
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        Distribution likelihood = graph.likelihoodDistributions.getFirst();
        assertEquals(
                9.0,
                discrete
                        ? assertInstanceOf(IntUniform.class, likelihood)
                                .paramInput
                                .get()
                                .get()
                        : assertInstanceOf(Uniform.class, likelihood)
                                .paramInput
                                .get()
                                .get());
        assertEquals(-Math.log(discrete ? 10.0 : 9.0), likelihood.calculateLogP(), 1e-12);
    }

    private static String realLiteral(double value) {
        // The current script lexer accepts decimal literals, not scientific notation.
        String decimal = BigDecimal.valueOf(value).toPlainString();
        return decimal.contains(".") ? decimal : decimal + ".0";
    }

    private static State state(BEASTState graph, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static double gaussianLogDensity(double mean) {
        return -0.5 * Math.log(2.0 * Math.PI) - 0.5 * (4.0 - mean) * (4.0 - mean);
    }

    /** A fixed observation, y=4 and sigma=1, with either scalar type as its unknown mean. */
    public static class GaussianObservation extends Distribution {
        public final Input<StateNode> meanInput = new Input<>("mean", "Sampled mean", Input.Validate.REQUIRED);

        @Override
        public double calculateLogP() {
            return logP = gaussianLogDensity(value(meanInput.get()));
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

    private static RealScalarParam<?> real(BEASTState graph, String id) {
        return assertInstanceOf(RealScalarParam.class, node(graph, id));
    }

    private static IntScalarParam<?> integer(BEASTState graph, String id) {
        return assertInstanceOf(IntScalarParam.class, node(graph, id));
    }

    private static Operator operator(BEASTState graph, StateNode node) {
        List<Operator> operators = graph.operators.stream()
                .filter(operator -> operator.listStateNodes().contains(node))
                .toList();
        assertEquals(1, operators.size(), "A sampled scalar must have one correctly bound default operator.");
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
        var graph = evaluator.applyBestTiling(new BEASTState("uniform-sampling"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
