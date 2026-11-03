package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.distribution.Gamma;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.util.RPNcalculator;
import beastconfig.BEASTState;
import beastconfig.LoggerSelector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.ExpValue;
import tiling.OffsetValue;

class ExpTileTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 0.0, 1.0})
    void fixedInputKeepsTheExistingScalarPath(double x) throws Exception {
        BEASTState graph = build("Real x = " + x + "\n"
                + "Real y ~ Normal(mean=0.0, sd=exp(x=x)) observed as 2.0\n"
                + "Vector<Real> grid = linspace(start=0.0, end=exp(x=x), num=3)");
        Normal likelihood = likelihood(graph, "y_likelihood");
        RealScalarParam<?> result = assertInstanceOf(RealScalarParam.class, likelihood.sdInput.get());
        assertEquals(Math.exp(x), result.get(), 0.0);
        assertSame(PositiveReal.INSTANCE, result.getDomain());
        assertTrue(graph.calculationNodes.isEmpty());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(normalLogDensity(2.0, Math.exp(x)), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 0.0, 1.0})
    void literalInputProducesTheExpectedObservedLikelihood(double x) throws Exception {
        BEASTState graph = build("Real y ~ Normal(mean=0.0, sd=exp(x=" + x + ")) observed as 2.0\n");
        Normal likelihood = likelihood(graph, "y_likelihood");
        RealScalarParam<?> result = assertInstanceOf(RealScalarParam.class, likelihood.sdInput.get());
        assertEquals(Math.exp(x), result.get(), 0.0);
        assertSame(PositiveReal.INSTANCE, result.getDomain());
        assertTrue(graph.calculationNodes.isEmpty());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(normalLogDensity(2.0, Math.exp(x)), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"exp(x=0, typo=1)", "exp(x=0, x=1)", "exp()", "exp(0, 1)", "exp(x~0)"})
    void invalidLiteralCallsAreRejectedBeforeBackendConstruction(String call) {
        assertThrows(TypeError.class, () -> build("Real y ~ Normal(mean=0.0, sd=" + call + ") observed as 2.0\n"));
    }

    @ParameterizedTest
    @CsvSource({"Normal, Real", "Gamma, PositiveReal", "Exponential, NonNegativeReal", "Beta, Probability"})
    void sharedInputUpdatesLikelihoodsAndRestoresAcceptedAndRejectedStates(String family, String declaredType)
            throws Exception {
        String priorCall =
                switch (family) {
                    case "Normal" -> "Normal(mean=0.0, sd=1.0)";
                    case "Gamma" -> "Gamma(shape=2.0, rate=1.0)";
                    case "Exponential" -> "Exponential(rate=2.0)";
                    case "Beta" -> "Beta(alpha=2.0, beta=2.0)";
                    default -> throw new IllegalArgumentException(family);
                };
        BEASTState graph = build(declaredType + " x ~ " + priorCall + "\n" + """
                PositiveReal r = exp(x=x)
                PositiveReal s = exp(x=x)
                Real y ~ Normal(mean=0.0, sd=r) observed as 0.0
                Real z ~ Normal(mean=0.0, sd=s) observed as 2.0
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Distribution prior = graph.priorDistributions.get(x);
        Normal firstLikelihood = likelihood(graph, "y_likelihood");
        Normal secondLikelihood = likelihood(graph, "z_likelihood");
        ExpValue first = assertInstanceOf(ExpValue.class, firstLikelihood.sdInput.get());
        ExpValue second = assertInstanceOf(ExpValue.class, secondLikelihood.sdInput.get());
        assertNotSame(first, second);
        assertSame(x, first.xInput.get());
        assertSame(x, second.xInput.get());
        Real expectedDomain =
                switch (family) {
                    case "Normal" -> Real.INSTANCE;
                    case "Gamma" -> PositiveReal.INSTANCE;
                    case "Exponential" -> NonNegativeReal.INSTANCE;
                    case "Beta" -> UnitInterval.INSTANCE;
                    default -> throw new IllegalArgumentException(family);
                };
        assertSame(expectedDomain, x.getDomain());
        assertSame(PositiveReal.INSTANCE, first.getDomain());
        assertSame(PositiveReal.INSTANCE, second.getDomain());
        assertTrue(x.getOutputs().containsAll(List.of(first, second)));
        assertTrue(first.getOutputs().contains(firstLikelihood));
        assertTrue(second.getOutputs().contains(secondLikelihood));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(2, graph.calculationNodes.size());
        assertTrue(graph.calculationNodes.keySet().containsAll(List.of(first, second)));
        assertEquals(1, graph.operators.size());
        ScaleOperator operator = assertInstanceOf(ScaleOperator.class, graph.operators.getFirst());
        assertEquals(List.of(x), operator.listStateNodes());
        assertEquals(5.0, operator.m_pWeight.get(), 0.0);
        double initialValue =
                switch (family) {
                    case "Normal" -> 0.1;
                    case "Beta" -> 0.5;
                    default -> 1.0;
                };
        assertEquals(initialValue, x.get(), 0.0, "The parent's existing initialization must be preserved.");
        Object priorParameter =
                switch (family) {
                    case "Normal" ->
                        assertInstanceOf(Normal.class, prior).paramInput.get();
                    case "Gamma" ->
                        assertInstanceOf(Gamma.class, prior).paramInput.get();
                    case "Exponential" ->
                        assertInstanceOf(Exponential.class, prior).paramInput.get();
                    case "Beta" ->
                        assertInstanceOf(Beta.class, prior).paramInput.get();
                    default -> throw new IllegalArgumentException(family);
                };
        assertSame(x, priorParameter);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, firstLikelihood, secondLikelihood));
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(expectedPosterior(family, x.get()), state.robustlyCalcPosterior(posterior), TOLERANCE);

        // Controlled updates test dependencies and restoration, not the operator's ability to cross zero.
        double[] proposals =
                switch (family) {
                    case "Normal" -> new double[] {-1.0, 0.0, 1.0, 0.5};
                    case "Gamma" -> new double[] {0.25, 0.5, 1.5, 1.25};
                    case "Exponential" -> new double[] {0.0, 0.5, 1.5, 1.0};
                    case "Beta" -> new double[] {0.2, 0.4, 0.7, 0.8};
                    default -> throw new IllegalArgumentException(family);
                };
        boolean[] decisions = {true, true, false, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousX = x.get();
            double previousPrior = prior.getCurrentLogP();
            double previousFirstLikelihood = firstLikelihood.getCurrentLogP();
            double previousSecondLikelihood = secondLikelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            x.set(proposals[step]);
            assertEquals(Math.exp(proposals[step]), first.get(), 0.0);
            assertEquals(Math.exp(proposals[step]), second.get(), 0.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertTrue(first.somethingIsDirty());
            assertTrue(second.somethingIsDirty());

            double expectedPrior = priorLogDensity(family, proposals[step]);
            double expectedFirst = normalLogDensity(0.0, Math.exp(proposals[step]));
            double expectedSecond = normalLogDensity(2.0, Math.exp(proposals[step]));
            double proposedPosterior = posterior.calculateLogP();
            assertTrue(Double.isFinite(proposedPosterior));
            assertNotEquals(previousFirstLikelihood, expectedFirst, TOLERANCE);
            assertNotEquals(previousSecondLikelihood, expectedSecond, TOLERANCE);
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedFirst, firstLikelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedSecond, secondLikelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedFirst + expectedSecond, proposedPosterior, TOLERANCE);
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            double currentX = decisions[step] ? proposals[step] : previousX;
            assertEquals(currentX, x.get(), 0.0);
            assertEquals(Math.exp(currentX), first.get(), 0.0);
            assertEquals(Math.exp(currentX), second.get(), 0.0);
            assertSame(x, first.xInput.get());
            assertSame(x, second.xInput.get());
            assertEquals(0.0, firstLikelihood.paramInput.get().get(), 0.0);
            assertEquals(2.0, secondLikelihood.paramInput.get().get(), 0.0);

            // Inspect restored caches before any density calculation can hide a stale result.
            assertEquals(decisions[step] ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    decisions[step] ? expectedFirst : previousFirstLikelihood,
                    firstLikelihood.getCurrentLogP(),
                    TOLERANCE);
            assertEquals(
                    decisions[step] ? expectedSecond : previousSecondLikelihood,
                    secondLikelihood.getCurrentLogP(),
                    TOLERANCE);
            assertEquals(
                    decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(normalLogDensity(0.0, Math.exp(currentX)), firstLikelihood.calculateLogP(), TOLERANCE);
            assertEquals(normalLogDensity(2.0, Math.exp(currentX)), secondLikelihood.calculateLogP(), TOLERANCE);
            assertEquals(expectedPosterior(family, currentX), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 0.0, 1.0})
    void inlineAndSharedAssignedValuesKeepTheOriginalInput(double proposedX) throws Exception {
        BEASTState graph = build("""
                Real x ~ Normal(mean=0.0, sd=1.0)
                PositiveReal sigma = exp(x=x)
                Real y ~ Normal(mean=0.0, sd=sigma) observed as 0.0
                Real z ~ Normal(mean=0.0, sd=sigma) observed as 2.0
                Real w ~ Normal(mean=0.0, sd=exp(x=x)) observed as -1.0
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Normal firstLikelihood = likelihood(graph, "y_likelihood");
        Normal secondLikelihood = likelihood(graph, "z_likelihood");
        Normal inlineLikelihood = likelihood(graph, "w_likelihood");
        ExpValue shared = assertInstanceOf(ExpValue.class, firstLikelihood.sdInput.get());
        ExpValue inline = assertInstanceOf(ExpValue.class, inlineLikelihood.sdInput.get());
        assertSame(shared, secondLikelihood.sdInput.get());
        assertNotSame(shared, inline);
        assertSame(x, shared.xInput.get());
        assertSame(x, inline.xInput.get());
        assertTrue(shared.getOutputs().containsAll(List.of(firstLikelihood, secondLikelihood)));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(2, graph.calculationNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        LoggerSelector.addMissingLoggerSpecs(graph);
        assertTrue(graph.fileLoggerSpecs.getFirst().parameters().containsAll(List.of(shared, inline)));
        assertTrue(graph.screenLoggerSpecs.getFirst().parameters().containsAll(List.of(shared, inline)));

        x.set(proposedX);
        double expected = Math.exp(proposedX);
        assertEquals(expected, shared.get(), 0.0);
        assertEquals(expected, inline.get(), 0.0);
        assertEquals(normalLogDensity(0.0, expected), firstLikelihood.calculateLogP(), TOLERANCE);
        assertEquals(normalLogDensity(2.0, expected), secondLikelihood.calculateLogP(), TOLERANCE);
        assertEquals(normalLogDensity(-1.0, expected), inlineLikelihood.calculateLogP(), TOLERANCE);
        assertEquals(expected, loggedValue(shared), 0.0);
        assertEquals(expected, loggedValue(inline), 0.0);
    }

    @Test
    void standaloneAndArithmeticExpressionsKeepTheExistingRpnPath() throws Exception {
        // Keep an independent check of the existing RPN multiplication path.
        BEASTState graph = build("""
                Real x ~ Normal(mean=0.0, sd=1.0)
                Real direct = exp(x=x)
                Real arithmetic = exp(x=x * 2.0)
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        RPNcalculator direct = rpn(graph, "direct");
        RPNcalculator arithmetic = rpn(graph, "arithmetic");
        assertTrue(direct.parametersInput.get().contains(x));
        assertTrue(arithmetic.parametersInput.get().contains(x));
        assertEquals(2, graph.calculationNodes.size());
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        ScaleOperator operator = assertInstanceOf(ScaleOperator.class, graph.operators.getFirst());
        assertEquals(List.of(x), operator.listStateNodes());
        assertEquals(5.0, operator.m_pWeight.get(), 0.0);
        Normal prior = assertInstanceOf(Normal.class, graph.priorDistributions.get(x));
        assertSame(x, prior.paramInput.get());
        assertSame(Real.INSTANCE, x.getDomain());
        assertEquals(0.1, x.get(), 0.0);
        assertEquals(0.0, prior.meanInput.get().get(), 0.0);
        assertEquals(1.0, prior.sdInput.get().get(), 0.0);
        for (double value : new double[] {-1.0, 0.0, 1.0}) {
            x.set(value);
            assertEquals(normalLogDensity(value, 1.0), prior.calculateLogP(), TOLERANCE);
            assertEquals(Math.exp(value), direct.getStatisticValue(0), TOLERANCE);
            assertEquals(Math.exp(value * 2.0), arithmetic.getStatisticValue(0), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shiftedAndUnshiftedExponentialsKeepTheOriginalStateInEitherOrder(boolean shiftedFirst) throws Exception {
        String directDeclaration = "Real direct = exp(x=x)\n";
        String shiftedDeclaration = "Real shifted = exp(x=x + 1.0)\n";
        BEASTState graph = build("Real x ~ Normal(mean=0.0, sd=1.0)\n"
                + (shiftedFirst ? shiftedDeclaration + directDeclaration : directDeclaration + shiftedDeclaration));
        RealScalarParam<?> x = parameter(graph, "x");
        RPNcalculator direct = rpn(graph, "direct");
        RPNcalculator shifted = rpn(graph, "shifted");
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        Normal prior = assertInstanceOf(Normal.class, graph.priorDistributions.get(x));
        assertSame(x, prior.paramInput.get());
        assertSame(Real.INSTANCE, x.getDomain());
        assertEquals(0.1, x.get(), 0.0);
        assertEquals(0.0, prior.meanInput.get().get(), 0.0);
        assertEquals(1.0, prior.sdInput.get().get(), 0.0);
        ScaleOperator operator = assertInstanceOf(ScaleOperator.class, graph.operators.getFirst());
        assertEquals(List.of(x), operator.listStateNodes());
        assertEquals(5.0, operator.m_pWeight.get(), 0.0);

        // RPN can either inline the addition or read an OffsetValue. Both must retain the same source state.
        for (var node : graph.calculationNodes.keySet()) {
            if (node instanceof OffsetValue offset) {
                assertSame(x, offset.xInput.get());
                assertEquals(1.0, offset.offsetInput.get(), 0.0);
            }
        }
        for (double value : new double[] {-1.0, 0.0, 1.0}) {
            x.set(value);
            assertEquals("x", x.getID(), "Derived expressions must not rename their sampled input.");
            assertEquals(normalLogDensity(value, 1.0), prior.calculateLogP(), TOLERANCE);
            assertEquals(Math.exp(value), direct.getStatisticValue(0), TOLERANCE);
            assertEquals(Math.exp(value + 1.0), shifted.getStatisticValue(0), TOLERANCE);
        }
    }

    private static double expectedPosterior(String family, double x) {
        return priorLogDensity(family, x) + normalLogDensity(0.0, Math.exp(x)) + normalLogDensity(2.0, Math.exp(x));
    }

    private static double priorLogDensity(String family, double x) {
        return switch (family) {
            case "Normal" -> normalLogDensity(x, 1.0);
            case "Gamma" -> Math.log(x) - x;
            case "Exponential" -> Math.log(2.0) - 2.0 * x;
            case "Beta" -> Math.log(6.0) + Math.log(x) + Math.log1p(-x);
            default -> throw new IllegalArgumentException(family);
        };
    }

    private static double normalLogDensity(double observation, double sd) {
        double z = observation / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - 0.5 * z * z;
    }

    private static double loggedValue(ExpValue value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }

    private static Normal likelihood(BEASTState graph, String id) {
        return assertInstanceOf(
                Normal.class,
                graph.likelihoodDistributions.stream()
                        .filter(distribution -> id.equals(distribution.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static RealScalarParam<?> parameter(BEASTState graph, String id) {
        return assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> id.equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private static RPNcalculator rpn(BEASTState graph, String id) {
        return assertInstanceOf(
                RPNcalculator.class,
                graph.calculationNodes.keySet().stream()
                        .filter(node -> id.equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("exp-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
