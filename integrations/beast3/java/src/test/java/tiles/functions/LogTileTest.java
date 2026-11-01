package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.ExpValue;
import tiling.LogValue;

class LogTileTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 1.0, 2.0})
    void fixedInputKeepsTheExistingScalarPath(double x) throws Exception {
        BEASTState graph =
                build("PositiveReal x = " + x + "\n" + "Real y ~ Normal(mean=log(x=x), sd=1.0) observed as 0.3\n");
        Normal likelihood = likelihood(graph, "y_likelihood");
        RealScalarParam<?> result = assertInstanceOf(RealScalarParam.class, likelihood.meanInput.get());
        assertEquals(Math.log(x), result.get(), 0.0);
        assertSame(Real.INSTANCE, result.getDomain());
        assertTrue(graph.calculationNodes.isEmpty());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(normalLogDensity(0.3, Math.log(x)), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 10})
    void fixedInputKeepsTheExistingExplicitBasePath(int base) throws Exception {
        BEASTState graph = build("PositiveReal x = 100.0\n" + "Real y ~ Normal(mean=log(x=x, base=" + base
                + "), sd=1.0) observed as 0.3\n");
        Normal likelihood = likelihood(graph, "y_likelihood");
        RealScalarParam<?> result = assertInstanceOf(RealScalarParam.class, likelihood.meanInput.get());
        double expected = Math.log(100.0) / Math.log(base);
        assertEquals(expected, result.get(), 0.0);
        assertSame(Real.INSTANCE, result.getDomain());
        assertTrue(graph.calculationNodes.isEmpty());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(normalLogDensity(0.3, expected), likelihood.calculateLogP(), TOLERANCE);
    }

    @Test
    void sharedAndInlineValuesUpdateLikelihoodsAndRestoreAcceptedAndRejectedStates() throws Exception {
        BEASTState graph = build("""
                PositiveReal x ~ Gamma(shape=2.0, rate=1.0)
                Real mu = log(x=x)
                Real y ~ Normal(mean=mu, sd=1.0) observed as 0.3
                Real z ~ Normal(mean=mu, sd=1.0) observed as 2.0
                Real w ~ Normal(mean=log(x=x), sd=1.0) observed as -1.0
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Gamma prior = assertOriginalGammaDraw(graph, x);
        Normal first = likelihood(graph, "y_likelihood");
        Normal second = likelihood(graph, "z_likelihood");
        Normal third = likelihood(graph, "w_likelihood");
        LogValue shared = assertInstanceOf(LogValue.class, first.meanInput.get());
        LogValue inline = assertInstanceOf(LogValue.class, third.meanInput.get());
        assertSame(shared, second.meanInput.get());
        assertNotSame(shared, inline);
        assertSame(x, shared.xInput.get());
        assertSame(x, inline.xInput.get());
        assertSame(Real.INSTANCE, shared.getDomain());
        assertSame(Real.INSTANCE, inline.getDomain());
        assertTrue(x.getOutputs().containsAll(List.of(prior, shared, inline)));
        assertTrue(shared.getOutputs().containsAll(List.of(first, second)));
        assertTrue(inline.getOutputs().contains(third));
        assertEquals(2, graph.calculationNodes.size());
        assertTrue(graph.calculationNodes.keySet().containsAll(List.of(shared, inline)));
        assertEquals(3, graph.likelihoodDistributions.size());

        List<Normal> likelihoods = List.of(first, second, third);
        List<Distribution> terms = new ArrayList<>();
        terms.add(prior);
        terms.addAll(likelihoods);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", terms);
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(expectedPosterior(x.get()), state.robustlyCalcPosterior(posterior), TOLERANCE);

        // Controlled proposals exercise negative, zero and positive log values without changing operator policy.
        double[] proposals = {0.5, 1.0, 2.0, 4.0};
        boolean[] decisions = {true, true, false, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousX = x.get();
            double previousPrior = prior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            double[] previousLikelihoods = likelihoods.stream()
                    .mapToDouble(Distribution::getCurrentLogP)
                    .toArray();
            state.store(step + 1);
            x.set(proposals[step]);
            double expectedMean = Math.log(proposals[step]);
            assertEquals(expectedMean, shared.get(), 0.0);
            assertEquals(expectedMean, inline.get(), 0.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertTrue(shared.somethingIsDirty());
            assertTrue(inline.somethingIsDirty());

            double expectedPrior = gammaLogDensity(proposals[step]);
            double[] expectedLikelihoods = expectedLikelihoods(proposals[step]);
            double proposedPosterior = posterior.calculateLogP();
            assertTrue(Double.isFinite(proposedPosterior));
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            for (int i = 0; i < likelihoods.size(); i++) {
                assertNotEquals(previousLikelihoods[i], expectedLikelihoods[i], TOLERANCE);
                assertEquals(expectedLikelihoods[i], likelihoods.get(i).getCurrentLogP(), TOLERANCE);
            }
            assertEquals(expectedPosterior(proposals[step]), proposedPosterior, TOLERANCE);
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            double currentX = decisions[step] ? proposals[step] : previousX;
            assertEquals(currentX, x.get(), 0.0);
            assertEquals(Math.log(currentX), shared.get(), 0.0);
            assertEquals(Math.log(currentX), inline.get(), 0.0);
            assertEquals("x", x.getID());
            assertSame(x, prior.paramInput.get());
            assertSame(x, shared.xInput.get());
            assertSame(x, inline.xInput.get());
            assertEquals(0.3, first.paramInput.get().get(), 0.0);
            assertEquals(2.0, second.paramInput.get().get(), 0.0);
            assertEquals(-1.0, third.paramInput.get().get(), 0.0);

            // Check caches before density calculations can hide stale results after rejection.
            assertEquals(decisions[step] ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            for (int i = 0; i < likelihoods.size(); i++) {
                assertEquals(
                        decisions[step] ? expectedLikelihoods[i] : previousLikelihoods[i],
                        likelihoods.get(i).getCurrentLogP(),
                        TOLERANCE);
            }
            assertEquals(
                    decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            double[] currentLikelihoods = expectedLikelihoods(currentX);
            for (int i = 0; i < likelihoods.size(); i++) {
                assertEquals(currentLikelihoods[i], likelihoods.get(i).calculateLogP(), TOLERANCE);
            }
            assertEquals(gammaLogDensity(currentX), prior.calculateLogP(), TOLERANCE);
            assertEquals(expectedPosterior(currentX), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void nestedExponentialInputStaysLiveAndCanBeLogged() throws Exception {
        BEASTState graph = build("""
                Real eta ~ Normal(mean=0.0, sd=1.0)
                PositiveReal positive = exp(x=eta)
                Real mu = log(x=positive)
                Real y ~ Normal(mean=mu, sd=1.0) observed as 0.3
                """);
        RealScalarParam<?> eta = parameter(graph, "eta");
        Normal likelihood = likelihood(graph, "y_likelihood");
        LogValue result = assertInstanceOf(LogValue.class, likelihood.meanInput.get());
        ExpValue input = assertInstanceOf(ExpValue.class, result.xInput.get());
        assertSame(eta, input.xInput.get());
        assertTrue(eta.getOutputs().contains(input));
        assertTrue(input.getOutputs().contains(result));
        assertTrue(result.getOutputs().contains(likelihood));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        LoggerSelector.addMissingLoggerSpecs(graph);
        assertTrue(graph.fileLoggerSpecs.getFirst().parameters().contains(result));
        assertTrue(graph.screenLoggerSpecs.getFirst().parameters().contains(result));
        for (double value : new double[] {-1.0, 0.0, 1.0}) {
            eta.set(value);
            assertEquals(value, result.get(), TOLERANCE);
            assertEquals(normalLogDensity(0.3, value), likelihood.calculateLogP(), TOLERANCE);
            assertEquals(value, loggedValue(result), TOLERANCE);
            assertEquals("eta", eta.getID());
        }
    }

    @Test
    void standaloneAndArithmeticExpressionsKeepTheExistingRpnPath() throws Exception {
        BEASTState graph = build("""
                PositiveReal x ~ Gamma(shape=2.0, rate=1.0)
                Real direct = log(x=x)
                Real arithmetic = log(x=x * 2.0)
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Gamma prior = assertOriginalGammaDraw(graph, x);
        RPNcalculator direct = rpn(graph, "direct");
        RPNcalculator arithmetic = rpn(graph, "arithmetic");
        assertTrue(direct.parametersInput.get().contains(x));
        assertTrue(arithmetic.parametersInput.get().contains(x));
        for (double value : new double[] {0.5, 1.0, 2.0}) {
            x.set(value);
            assertEquals(Math.log(value), direct.getStatisticValue(0), TOLERANCE);
            assertEquals(Math.log(value * 2.0), arithmetic.getStatisticValue(0), TOLERANCE);
            assertEquals(gammaLogDensity(value), prior.calculateLogP(), TOLERANCE);
            assertEquals("x", x.getID());
        }
    }

    @Test
    void explicitBaseOnRandomInputIsNotSilentlyIgnored() {
        assertThrows(TileApplicationError.class, () -> build("""
                PositiveReal x ~ Gamma(shape=2.0, rate=1.0)
                Real mu = log(x=x, base=10)
                """));
    }

    private static Gamma assertOriginalGammaDraw(BEASTState graph, RealScalarParam<?> x) {
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        assertSame(PositiveReal.INSTANCE, x.getDomain());
        assertEquals(1.0, x.get(), 0.0, "The parent's existing initialization must be preserved.");
        Gamma prior = assertInstanceOf(Gamma.class, graph.priorDistributions.get(x));
        assertSame(x, prior.paramInput.get());
        assertEquals(2.0, prior.alphaInput.get().get(), 0.0);
        assertEquals(1.0, prior.betaInput.get().get(), 0.0);
        ScaleOperator operator = assertInstanceOf(ScaleOperator.class, graph.operators.getFirst());
        assertEquals(List.of(x), operator.listStateNodes());
        assertEquals(5.0, operator.m_pWeight.get(), 0.0);
        return prior;
    }

    private static double[] expectedLikelihoods(double x) {
        double mean = Math.log(x);
        return new double[] {normalLogDensity(0.3, mean), normalLogDensity(2.0, mean), normalLogDensity(-1.0, mean)};
    }

    private static double expectedPosterior(double x) {
        double result = gammaLogDensity(x);
        for (double likelihood : expectedLikelihoods(x)) result += likelihood;
        return result;
    }

    private static double gammaLogDensity(double x) {
        // Gamma(shape=2, rate=1), including its normalization constant.
        return Math.log(x) - x;
    }

    private static double normalLogDensity(double value, double mean) {
        double residual = value - mean;
        return -0.5 * Math.log(2.0 * Math.PI) - 0.5 * residual * residual;
    }

    private static double loggedValue(LogValue value) {
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("log-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
