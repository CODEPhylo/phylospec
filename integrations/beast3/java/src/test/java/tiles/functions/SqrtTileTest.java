package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.Cauchy;
import beast.base.spec.inference.distribution.Exponential;
import beast.base.spec.inference.distribution.Gamma;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
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
import tiling.SqrtValue;

class SqrtTileTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 4.0, 9.0})
    void fixedInputKeepsTheExistingScalarPath(double x) throws Exception {
        BEASTState graph = build("NonNegativeReal x = " + x + "\n"
                + "Real y ~ Cauchy(location=sqrt(x=x), scale=1.0) observed as 2.0\n"
                + "Vector<Real> grid = linspace(start=0.0, end=sqrt(x=x), num=3)");
        Cauchy likelihood = likelihood(graph, "y_likelihood");
        RealScalarParam<?> result = assertInstanceOf(RealScalarParam.class, likelihood.locationInput.get());
        assertEquals(Math.sqrt(x), result.get(), 0.0);
        assertSame(NonNegativeReal.INSTANCE, result.getDomain());
        assertTrue(graph.calculationNodes.isEmpty());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(cauchyLogDensity(2.0, Math.sqrt(x), 1.0), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 4.0, 0.25})
    void literalInputProducesTheExpectedObservedLikelihood(double x) throws Exception {
        BEASTState graph = build("Real y ~ Cauchy(location=sqrt(x=" + x + "), scale=1.0) observed as 2.0\n");
        Cauchy likelihood = likelihood(graph, "y_likelihood");
        RealScalarParam<?> result = assertInstanceOf(RealScalarParam.class, likelihood.locationInput.get());
        assertEquals(Math.sqrt(x), result.get(), 0.0);
        assertTrue(graph.calculationNodes.isEmpty());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(cauchyLogDensity(2.0, Math.sqrt(x), 1.0), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sqrt(x=-1)", "sqrt(x=4, typo=1)", "sqrt(x=4, x=9)", "sqrt()", "sqrt(4, 9)", "sqrt(x~4)"})
    void invalidLiteralCallsAreRejectedBeforeBackendConstruction(String call) {
        assertThrows(
                TypeError.class, () -> build("Real y ~ Cauchy(location=" + call + ", scale=1.0) observed as 2.0\n"));
    }

    @ParameterizedTest
    @CsvSource({"Gamma, PositiveReal", "Exponential, NonNegativeReal"})
    void sharedInputUpdatesLikelihoodsAndRestoresAcceptedAndRejectedStates(String family, String declaredType)
            throws Exception {
        String priorCall = family.equals("Gamma") ? "Gamma(shape=2.0, rate=1.0)" : "Exponential(rate=2.0)";
        BEASTState graph = build(declaredType + " x ~ " + priorCall + "\n" + """
                NonNegativeReal r = sqrt(x=x)
                NonNegativeReal s = sqrt(x=x)
                Real y ~ Cauchy(location=r, scale=1.0) observed as 2.0
                Real z ~ Cauchy(location=s, scale=0.5) observed as -1.0
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Distribution prior = graph.priorDistributions.get(x);
        Cauchy firstLikelihood = likelihood(graph, "y_likelihood");
        Cauchy secondLikelihood = likelihood(graph, "z_likelihood");
        SqrtValue first = assertInstanceOf(SqrtValue.class, firstLikelihood.locationInput.get());
        SqrtValue second = assertInstanceOf(SqrtValue.class, secondLikelihood.locationInput.get());
        assertNotSame(first, second);
        assertSame(x, first.xInput.get());
        assertSame(x, second.xInput.get());
        assertSame(family.equals("Gamma") ? PositiveReal.INSTANCE : NonNegativeReal.INSTANCE, x.getDomain());
        assertSame(NonNegativeReal.INSTANCE, first.getDomain());
        assertSame(NonNegativeReal.INSTANCE, second.getDomain());
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
        assertEquals(1.0, x.get(), 0.0, "The parent's existing initialization must be preserved.");
        if (family.equals("Gamma")) {
            assertSame(x, assertInstanceOf(Gamma.class, prior).paramInput.get());
        } else {
            assertSame(x, assertInstanceOf(Exponential.class, prior).paramInput.get());
        }

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(prior, firstLikelihood, secondLikelihood));
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        assertEquals(expectedPosterior(family, x.get()), state.robustlyCalcPosterior(posterior), TOLERANCE);

        // Explicit moves isolate the dependency and restoration checks from operator policy.
        // The non-negative input visits zero, where the result must remain exactly zero.
        double[] proposals = family.equals("Gamma") ? new double[] {4.0, 9.0, 2.25} : new double[] {0.0, 9.0, 4.0};
        boolean[] decisions = {true, false, true};
        for (int step = 0; step < proposals.length; step++) {
            double previousX = x.get();
            double previousPrior = prior.getCurrentLogP();
            double previousFirstLikelihood = firstLikelihood.getCurrentLogP();
            double previousSecondLikelihood = secondLikelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            x.set(proposals[step]);
            assertEquals(Math.sqrt(proposals[step]), first.get(), 0.0);
            assertEquals(Math.sqrt(proposals[step]), second.get(), 0.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertTrue(first.somethingIsDirty());
            assertTrue(second.somethingIsDirty());

            double expectedPrior = priorLogDensity(family, proposals[step]);
            double expectedFirst = cauchyLogDensity(2.0, Math.sqrt(proposals[step]), 1.0);
            double expectedSecond = cauchyLogDensity(-1.0, Math.sqrt(proposals[step]), 0.5);
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
            assertEquals(Math.sqrt(currentX), first.get(), 0.0);
            assertEquals(Math.sqrt(currentX), second.get(), 0.0);
            assertSame(x, first.xInput.get());
            assertSame(x, second.xInput.get());
            assertEquals(2.0, firstLikelihood.paramInput.get().get(), 0.0);
            assertEquals(-1.0, secondLikelihood.paramInput.get().get(), 0.0);

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
            assertEquals(cauchyLogDensity(2.0, Math.sqrt(currentX), 1.0), firstLikelihood.calculateLogP(), TOLERANCE);
            assertEquals(cauchyLogDensity(-1.0, Math.sqrt(currentX), 0.5), secondLikelihood.calculateLogP(), TOLERANCE);
            assertEquals(expectedPosterior(family, currentX), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void nestedLiveValuesAreConnectedAndIncludedInDefaultLogging() throws Exception {
        BEASTState graph = build("""
                PositiveReal x ~ Gamma(shape=2.0, rate=1.0)
                NonNegativeReal r = sqrt(x=x)
                NonNegativeReal nested = sqrt(x=r)
                Real y ~ Cauchy(location=nested, scale=1.0) observed as 2.0
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Cauchy likelihood = likelihood(graph, "y_likelihood");
        SqrtValue nested = assertInstanceOf(SqrtValue.class, likelihood.locationInput.get());
        SqrtValue first = assertInstanceOf(SqrtValue.class, nested.xInput.get());
        assertSame(x, first.xInput.get());
        assertTrue(first.getOutputs().contains(nested));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(2, graph.calculationNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        LoggerSelector.addMissingLoggerSpecs(graph);
        assertTrue(graph.fileLoggerSpecs.getFirst().parameters().containsAll(List.of(first, nested)));
        assertTrue(graph.screenLoggerSpecs.getFirst().parameters().containsAll(List.of(first, nested)));
        x.set(16.0);
        assertEquals(4.0, first.get(), 0.0);
        assertEquals(2.0, nested.get(), 0.0);
        assertEquals(cauchyLogDensity(2.0, 2.0, 1.0), likelihood.calculateLogP(), TOLERANCE);
        assertEquals(4.0, loggedValue(first), 0.0);
        assertEquals(2.0, loggedValue(nested), 0.0);
    }

    @Test
    void negativeInputStillFailsCoreTypeChecking() {
        assertThrows(TypeError.class, () -> build("""
                Real x = -4.0
                NonNegativeReal r = sqrt(x=x)
                """));
    }

    private static double expectedPosterior(String family, double x) {
        return priorLogDensity(family, x)
                + cauchyLogDensity(2.0, Math.sqrt(x), 1.0)
                + cauchyLogDensity(-1.0, Math.sqrt(x), 0.5);
    }

    private static double priorLogDensity(String family, double x) {
        return family.equals("Gamma") ? Math.log(x) - x : Math.log(2.0) - 2.0 * x;
    }

    private static double cauchyLogDensity(double observation, double location, double scale) {
        double z = (observation - location) / scale;
        return -Math.log(Math.PI) - Math.log(scale) - Math.log1p(z * z);
    }

    private static double loggedValue(SqrtValue value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }

    private static Cauchy likelihood(BEASTState graph, String id) {
        return assertInstanceOf(
                Cauchy.class,
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("sqrt-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
