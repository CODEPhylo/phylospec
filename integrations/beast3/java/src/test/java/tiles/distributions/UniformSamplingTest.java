package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Input;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.distribution.Uniform;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import operators.IntScalarUniformOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Expr;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BoundDistribution;

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

    @ParameterizedTest
    @CsvSource({
        "Gamma, PositiveReal, false",
        "Gamma, PositiveReal, true",
        "Exponential, NonNegativeReal, false",
        "Exponential, NonNegativeReal, true",
        "Beta, Probability, false",
        "Beta, Probability, true"
    })
    void narrowerBoundsStayLiveThroughAcceptanceAndRejection(String family, String declaredType, boolean lowerBound)
            throws Exception {
        String upstreamCall =
                switch (family) {
                    case "Gamma" -> "Gamma(shape=2.0, rate=1.0)";
                    case "Exponential" -> "Exponential(rate=2.0)";
                    case "Beta" -> "Beta(alpha=2.0, beta=2.0)";
                    default -> throw new IllegalArgumentException(family);
                };
        Real domain =
                switch (family) {
                    case "Gamma" -> PositiveReal.INSTANCE;
                    case "Exponential" -> NonNegativeReal.INSTANCE;
                    case "Beta" -> UnitInterval.INSTANCE;
                    default -> throw new IllegalArgumentException(family);
                };
        double observed = lowerBound ? (family.equals("Beta") ? 0.6 : 1.2) : 0.4;
        String interval = lowerBound ? "lower=bound, upper=2.0" : "lower=0.0, upper=bound";
        BEASTState graph = build(declaredType + " bound ~ " + upstreamCall + "\n" + "Real y ~ Uniform(" + interval
                + ") observed as " + realLiteral(observed));
        RealScalarParam<?> bound = real(graph, "bound");
        Distribution boundPrior = graph.priorDistributions.get(bound);
        assertNotNull(boundPrior);
        Uniform likelihood = assertInstanceOf(Uniform.class, graph.likelihoodDistributions.getFirst());
        RealScalarParam<?> observation = assertInstanceOf(RealScalarParam.class, likelihood.paramInput.get());

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(1, graph.operators.size());
        assertEquals(List.of(bound), operator(graph, bound).listStateNodes());
        assertFalse(graph.stateNodes.containsKey(observation));
        assertSame(bound, lowerBound ? likelihood.lowerInput.get() : likelihood.upperInput.get());
        assertSame(domain, bound.getDomain());
        assertTrue(bound.getOutputs().contains(likelihood));
        assertEquals(observed, observation.get(), 0.0);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(boundPrior, likelihood));
        State state = state(graph, posterior);
        double initialWidth = lowerBound ? 2.0 - bound.get() : bound.get();
        assertEquals(
                boundLogDensity(family, bound.get()) - Math.log(initialWidth),
                state.robustlyCalcPosterior(posterior),
                1e-12);

        // The second proposal excludes the observation, but every interval remains valid.
        // The third is a finite-density rejection; the last checks movement after restoration.
        double[] proposals = lowerBound
                ? new double[] {0.2, family.equals("Beta") ? 0.8 : 1.5, 0.3, 0.4}
                : new double[] {0.8, 0.2, 0.6, 0.9};
        boolean[] decisions = {true, false, false, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousBound = bound.get();
            double previousPrior = boundPrior.getCurrentLogP();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            bound.set(proposals[step]);
            double lower = lowerBound ? proposals[step] : 0.0;
            double upper = lowerBound ? 2.0 : proposals[step];
            assertTrue(lower < upper);
            assertEquals(lower, likelihood.lowerInput.get().get(), 0.0);
            assertEquals(upper, likelihood.upperInput.get().get(), 0.0);
            assertEquals(observed, observation.get(), 0.0);

            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expectedPrior = boundLogDensity(family, proposals[step]);
            boolean outside = observed < lower || observed > upper;
            assertEquals(step == 1, outside);
            double expectedLikelihood = outside ? Double.NEGATIVE_INFINITY : -Math.log(upper - lower);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedPrior, boundPrior.getCurrentLogP(), 1e-12);
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), 1e-12);
            assertEquals(expectedPrior + expectedLikelihood, proposedPosterior, 1e-12);
            if (outside) {
                assertEquals(Double.NEGATIVE_INFINITY, proposedPosterior);
            } else {
                assertTrue(Double.isFinite(proposedPosterior));
                assertNotEquals(previousLikelihood, expectedLikelihood, 1e-12);
            }

            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            double currentBound = decisions[step] ? proposals[step] : previousBound;
            assertEquals(currentBound, bound.get(), 0.0);
            assertSame(bound, lowerBound ? likelihood.lowerInput.get() : likelihood.upperInput.get());
            assertSame(domain, bound.getDomain());
            assertEquals(observed, observation.get(), 0.0);
            assertEquals(
                    lowerBound ? currentBound : 0.0, likelihood.lowerInput.get().get(), 0.0);
            assertEquals(
                    lowerBound ? 2.0 : currentBound, likelihood.upperInput.get().get(), 0.0);

            // Verify caches before any recalculation can hide a failed restoration.
            assertEquals(decisions[step] ? expectedPrior : previousPrior, boundPrior.getCurrentLogP(), 1e-12);
            assertEquals(decisions[step] ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), 1e-12);
            assertEquals(decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), 1e-12);
            double currentWidth = lowerBound ? 2.0 - currentBound : currentBound;
            assertEquals(-Math.log(currentWidth), likelihood.calculateLogP(), 1e-12);
            assertEquals(
                    boundLogDensity(family, currentBound) - Math.log(currentWidth), posterior.calculateLogP(), 1e-12);
        }
    }

    @Test
    void twoNarrowerBoundsPreserveChildInitializationAndRestoreTogether() throws Exception {
        BEASTState graph = build("""
                Probability lower ~ Beta(alpha=2.0, beta=2.0)
                PositiveReal upper ~ Gamma(shape=2.0, rate=1.0)
                Real x ~ Uniform(lower=lower, upper=upper)
                """);
        RealScalarParam<?> lower = real(graph, "lower");
        RealScalarParam<?> upper = real(graph, "upper");
        RealScalarParam<?> x = real(graph, "x");
        Distribution lowerPrior = graph.priorDistributions.get(lower);
        Distribution upperPrior = graph.priorDistributions.get(upper);
        Uniform childPrior = assertInstanceOf(Uniform.class, graph.priorDistributions.get(x));
        assertEquals(3, graph.stateNodes.size());
        assertEquals(3, graph.priorDistributions.size());
        assertEquals(3, graph.operators.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(lower, childPrior.lowerInput.get());
        assertSame(upper, childPrior.upperInput.get());
        assertSame(UnitInterval.INSTANCE, lower.getDomain());
        assertSame(PositiveReal.INSTANCE, upper.getDomain());
        assertSame(Real.INSTANCE, x.getDomain());
        assertTrue(lower.getOutputs().contains(childPrior));
        assertTrue(upper.getOutputs().contains(childPrior));
        assertEquals(0.75, x.get(), 0.0);
        RealRandomWalkOperator childOperator = assertInstanceOf(RealRandomWalkOperator.class, operator(graph, x));
        assertEquals(0.125, childOperator.windowSizeInput.get(), 0.0);
        assertEquals(5.0, childOperator.m_pWeight.get(), 0.0);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(lowerPrior, upperPrior, childPrior));
        State state = state(graph, posterior);
        assertEquals(
                boundLogDensity("Beta", lower.get())
                        + boundLogDensity("Gamma", upper.get())
                        - Math.log(upper.get() - lower.get()),
                state.robustlyCalcPosterior(posterior),
                1e-12);
        double[][] proposals = {{0.2, 1.4}, {0.8, 0.9}, {0.3, 1.6}};
        boolean[] decisions = {true, false, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousLower = lower.get();
            double previousUpper = upper.get();
            double previousLowerPrior = lowerPrior.getCurrentLogP();
            double previousUpperPrior = upperPrior.getCurrentLogP();
            double previousChildPrior = childPrior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            lower.set(proposals[step][0]);
            upper.set(proposals[step][1]);
            assertTrue(lower.get() < upper.get());
            assertEquals(0.75, x.get(), 0.0, "Changing bounds must not reinitialize the sampled child.");
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expectedLowerPrior = boundLogDensity("Beta", proposals[step][0]);
            double expectedUpperPrior = boundLogDensity("Gamma", proposals[step][1]);
            double expectedChildPrior =
                    step == 1 ? Double.NEGATIVE_INFINITY : -Math.log(proposals[step][1] - proposals[step][0]);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedLowerPrior, lowerPrior.getCurrentLogP(), 1e-12);
            assertEquals(expectedUpperPrior, upperPrior.getCurrentLogP(), 1e-12);
            assertEquals(expectedChildPrior, childPrior.getCurrentLogP(), 1e-12);
            assertEquals(expectedLowerPrior + expectedUpperPrior + expectedChildPrior, proposedPosterior, 1e-12);
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(decisions[step] ? proposals[step][0] : previousLower, lower.get(), 0.0);
            assertEquals(decisions[step] ? proposals[step][1] : previousUpper, upper.get(), 0.0);
            assertEquals(0.75, x.get(), 0.0);
            assertSame(lower, childPrior.lowerInput.get());
            assertSame(upper, childPrior.upperInput.get());
            assertSame(UnitInterval.INSTANCE, lower.getDomain());
            assertSame(PositiveReal.INSTANCE, upper.getDomain());
            assertSame(Real.INSTANCE, x.getDomain());
            assertEquals(0.125, childOperator.windowSizeInput.get(), 0.0);
            assertEquals(5.0, childOperator.m_pWeight.get(), 0.0);

            // Both bound priors and the child prior must restore before any recomputation.
            assertEquals(decisions[step] ? expectedLowerPrior : previousLowerPrior, lowerPrior.getCurrentLogP(), 1e-12);
            assertEquals(decisions[step] ? expectedUpperPrior : previousUpperPrior, upperPrior.getCurrentLogP(), 1e-12);
            assertEquals(decisions[step] ? expectedChildPrior : previousChildPrior, childPrior.getCurrentLogP(), 1e-12);
            assertEquals(decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), 1e-12);
            assertEquals(-Math.log(upper.get() - lower.get()), childPrior.calculateLogP(), 1e-12);
            assertEquals(
                    boundLogDensity("Beta", lower.get())
                            + boundLogDensity("Gamma", upper.get())
                            - Math.log(upper.get() - lower.get()),
                    posterior.calculateLogP(),
                    1e-12);
        }
    }

    private static double boundLogDensity(String family, double value) {
        return switch (family) {
            case "Gamma" -> Math.log(value) - value;
            case "Exponential" -> Math.log(2.0) - 2.0 * value;
            case "Beta" -> Math.log(6.0) + Math.log(value) + Math.log1p(-value);
            default -> throw new IllegalArgumentException(family);
        };
    }

    @ParameterizedTest
    @CsvSource({"n, 5, 4", "1, n, 2", "n, n, 3"})
    void integerBoundsAcceptGeneratorResultsWithoutChangingInitialization(
            String lowerExpression, String upperExpression, int expectedInitial) throws Exception {
        String interval = "lower=" + lowerExpression + ", upper=" + upperExpression;
        BEASTState graph = build("NonNegativeInteger n = num(vector=[1, 2, 3])\n"
                + "Integer x ~ DiscreteUniform(" + interval + ")\n"
                + "Integer y ~ DiscreteUniform(" + interval + ") observed as 3");
        IntScalarParam<?> x = integer(graph, "x");
        IntUniform prior = assertInstanceOf(IntUniform.class, graph.priorDistributions.get(x));
        IntUniform likelihood = assertInstanceOf(IntUniform.class, graph.likelihoodDistributions.getFirst());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(1, graph.operators.size());
        assertEquals(expectedInitial, x.get());
        assertSame(Int.INSTANCE, x.getDomain());
        if (lowerExpression.equals("n")) {
            assertSame(NonNegativeInt.INSTANCE, prior.lowerInput.get().getDomain());
            assertSame(prior.lowerInput.get(), likelihood.lowerInput.get());
        }
        if (upperExpression.equals("n")) {
            assertSame(NonNegativeInt.INSTANCE, prior.upperInput.get().getDomain());
            assertSame(prior.upperInput.get(), likelihood.upperInput.get());
        }
        int lower = prior.lowerInput.get().get();
        int upper = prior.upperInput.get().get();
        double expectedLogP = -Math.log((long) upper - lower + 1);
        assertEquals(expectedLogP, prior.calculateLogP(), 1e-12);
        assertEquals(expectedLogP, likelihood.calculateLogP(), 1e-12);
        assertEquals(3, likelihood.paramInput.get().get());
        IntScalarUniformOperator childOperator = assertInstanceOf(IntScalarUniformOperator.class, operator(graph, x));
        assertEquals(10.0, childOperator.m_pWeight.get(), 0.0);
        x.set(lower);
        assertEquals(expectedLogP, prior.calculateLogP(), 1e-12);
        x.set(upper);
        assertEquals(expectedLogP, prior.calculateLogP(), 1e-12);
        if (lowerExpression.equals(upperExpression)) {
            assertSame(prior.lowerInput.get(), prior.upperInput.get());
            assertEquals(0.0, expectedLogP, 0.0);
            assertEquals(Double.NEGATIVE_INFINITY, childOperator.proposal());
            assertEquals(3, x.get());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "false, lower", "true, lower",
        "false, upper", "true, upper",
        "false, both", "true, both"
    })
    void narrowerIntegerBoundsStayLiveThroughAcceptanceAndRejection(boolean positive, String movingBounds) {
        boolean moveLower = !movingBounds.equals("upper");
        boolean moveUpper = !movingBounds.equals("lower");
        Int domain = positive ? PositiveInt.INSTANCE : NonNegativeInt.INSTANCE;
        IntScalarParam<?> lower = new IntScalarParam<>(moveLower ? 2 : 0, moveLower ? domain : Int.INSTANCE);
        IntScalarParam<?> upper = new IntScalarParam<>(moveUpper ? 6 : 8, moveUpper ? domain : Int.INSTANCE);
        BEASTState graph = new BEASTState("discrete-uniform-binding");
        BoundDistribution<IntScalarParam<Int>, IntUniform> boundDistribution = integerDistribution(graph, lower, upper);
        IntUniform likelihood = boundDistribution.getDistribution();
        IntScalarParam<Int> observation = new IntScalarParam<>(4, Int.INSTANCE);
        boundDistribution.observeAs(graph, observation, "y_likelihood");

        // Controlled moves exercise State's lifecycle without introducing a new operator.
        // Reuse the nonconstant test observation to expose stale posterior component caches.
        List<IntScalarParam<?>> moving =
                moveLower && moveUpper ? List.of(lower, upper) : List.of(moveLower ? lower : upper);
        List<GaussianObservation> evidence = new ArrayList<>();
        List<Distribution> components = new ArrayList<>();
        for (IntScalarParam<?> bound : moving) {
            graph.addStateNodeWithoutOperators(
                    bound,
                    TypeToken.parameterized(IntScalarParam.class, domain.getClass()),
                    bound == lower ? "lower" : "upper");
            GaussianObservation boundEvidence = new GaussianObservation();
            graph.setInput(boundEvidence, boundEvidence.meanInput, bound);
            graph.addLikelihoodDistribution(boundEvidence, bound.getID() + "_evidence");
            evidence.add(boundEvidence);
            components.add(boundEvidence);
        }
        // Calculate the evidence terms before an out-of-support value can short-circuit the sum.
        components.add(likelihood);
        graph.initializeBEASTObjects();
        assertEquals(moving.size(), graph.stateNodes.size());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertFalse(graph.stateNodes.containsKey(observation));
        assertSame(lower, likelihood.lowerInput.get());
        assertSame(upper, likelihood.upperInput.get());
        assertSame(observation, likelihood.paramInput.get());
        assertTrue(lower.getOutputs().contains(likelihood));
        assertTrue(upper.getOutputs().contains(likelihood));

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", components);
        State state = state(graph, posterior);
        double initialEvidence = moving.stream()
                .mapToDouble(bound -> gaussianLogDensity(bound.get()))
                .sum();
        assertEquals(
                initialEvidence - Math.log((long) upper.get() - lower.get() + 1),
                state.robustlyCalcPosterior(posterior),
                1e-12);
        int[][] proposals =
                switch (movingBounds) {
                    case "lower" -> new int[][] {{positive ? 1 : 0, 8}, {5, 8}, {3, 8}, {4, 8}};
                    case "upper" -> new int[][] {{0, 7}, {0, 3}, {0, 5}, {0, 4}};
                    case "both" -> new int[][] {{4, 4}, {5, 5}, {3, 5}, {2, 7}};
                    default -> throw new IllegalArgumentException(movingBounds);
                };
        // Accept, reject an out-of-support observation, reject a finite proposal, then accept.
        boolean[] decisions = {true, false, false, true};
        for (int step = 0; step < proposals.length; step++) {
            int previousLower = lower.get();
            int previousUpper = upper.get();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            List<Double> previousEvidence =
                    evidence.stream().map(Distribution::getCurrentLogP).toList();
            state.store(step + 1);
            if (moveLower) lower.set(proposals[step][0]);
            if (moveUpper) upper.set(proposals[step][1]);
            assertTrue(lower.get() <= upper.get());
            assertEquals(proposals[step][0], likelihood.lowerInput.get().get());
            assertEquals(proposals[step][1], likelihood.upperInput.get().get());
            assertEquals(4, observation.get());
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            boolean outside = observation.get() < lower.get() || observation.get() > upper.get();
            assertEquals(step == 1, outside);
            double expectedLikelihood =
                    outside ? Double.NEGATIVE_INFINITY : -Math.log((long) upper.get() - lower.get() + 1);
            List<Double> proposedEvidence = moving.stream()
                    .map(bound -> gaussianLogDensity(bound.get()))
                    .toList();
            double expectedEvidence =
                    proposedEvidence.stream().mapToDouble(Double::doubleValue).sum();
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), 1e-12);
            assertEquals(expectedEvidence + expectedLikelihood, proposedPosterior, 1e-12);
            for (int i = 0; i < evidence.size(); i++) {
                assertEquals(proposedEvidence.get(i), evidence.get(i).getCurrentLogP(), 1e-12);
            }
            if (!outside) assertNotEquals(previousLikelihood, expectedLikelihood, 1e-12);
            if (lower.get() == upper.get()) {
                assertEquals(outside ? Double.NEGATIVE_INFINITY : 0.0, expectedLikelihood, 0.0);
            }
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            assertEquals(decisions[step] ? proposals[step][0] : previousLower, lower.get());
            assertEquals(decisions[step] ? proposals[step][1] : previousUpper, upper.get());
            assertSame(lower, likelihood.lowerInput.get());
            assertSame(upper, likelihood.upperInput.get());
            for (IntScalarParam<?> bound : moving) assertSame(domain, bound.getDomain());
            assertEquals(4, observation.get());

            // Check stored caches before recalculation can conceal a restoration failure.
            assertEquals(decisions[step] ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), 1e-12);
            assertEquals(decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), 1e-12);
            for (int i = 0; i < evidence.size(); i++) {
                assertEquals(
                        decisions[step] ? proposedEvidence.get(i) : previousEvidence.get(i),
                        evidence.get(i).getCurrentLogP(),
                        1e-12);
            }
            double currentLikelihood = -Math.log((long) upper.get() - lower.get() + 1);
            assertEquals(currentLikelihood, likelihood.calculateLogP(), 1e-12);
            double currentEvidence = moving.stream()
                    .mapToDouble(bound -> gaussianLogDensity(bound.get()))
                    .sum();
            assertEquals(currentEvidence + currentLikelihood, posterior.calculateLogP(), 1e-12);
        }
    }

    private static BoundDistribution<IntScalarParam<Int>, IntUniform> integerDistribution(
            BEASTState graph, IntScalarParam<?> lower, IntScalarParam<?> upper) {
        DiscreteUniformTile tile = new DiscreteUniformTile();
        Tile<?, BEASTState> lowerTile = integerValueTile(lower);
        Tile<?, BEASTState> upperTile = integerValueTile(upper);
        // setTile alone bypasses matching. Check the declared inputs before wiring the fixture.
        tile.getGeneratorTileInputs();
        assertTrue(tile.lowerInput.getTypeToken().isAssignableFrom(lowerTile.getTypeToken()));
        assertTrue(tile.upperInput.getTypeToken().isAssignableFrom(upperTile.getTypeToken()));
        tile.lowerInput.setTile(lowerTile);
        tile.upperInput.setTile(upperTile);
        return tile.applyTile(graph, new IdentityHashMap<>());
    }

    private static Tile<IntScalarParam<?>, BEASTState> integerValueTile(IntScalarParam<?> value) {
        Tile<IntScalarParam<?>, BEASTState> tile = new Tile<>() {
            @Override
            public TypeToken<?> getTypeToken() {
                return TypeToken.parameterized(
                        IntScalarParam.class, value.getDomain().getClass());
            }

            @Override
            protected IntScalarParam<?> applyTile(BEASTState graph, IdentityHashMap<Expr.Variable, Integer> indexes) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
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
