package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.operator.RealRandomWalkOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.util.RPNcalculator;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.OffsetValue;

class OffsetTileTest {
    private static final double TOLERANCE = 1e-12;

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void sharedOffsetsPreserveTheOriginalPriorAndRestoreAcceptedAndRejectedStates(
            boolean constantFirst, boolean directObservationFirst) throws Exception {
        String sharedExpression = constantFirst ? "1.0 + x" : "x + 1.0";
        String otherExpression = constantFirst ? "x + 2.0" : "2.0 + x";
        String directObservation = "Real direct ~ Normal(mean=x, sd=1.0) observed as -2.0\n";
        String shiftedObservations = "Real shifted = " + sharedExpression + "\n"
                + "Real other = " + otherExpression + "\n"
                + "Real y ~ Normal(mean=shifted, sd=1.0) observed as 2.0\n"
                + "Real z ~ Normal(mean=shifted, sd=1.0) observed as -1.0\n"
                + "Real w ~ Normal(mean=other, sd=1.0) observed as 4.0\n";
        BEASTState graph = build("Real x ~ Normal(mean=0.0, sd=1.0)\n"
                + (directObservationFirst
                        ? directObservation + shiftedObservations
                        : shiftedObservations + directObservation));
        RealScalarParam<?> x = parameter(graph, "x");
        Normal prior = assertOriginalDraw(graph, x, 0.0, 1.0);
        Normal first = likelihood(graph, "y_likelihood");
        Normal second = likelihood(graph, "z_likelihood");
        Normal third = likelihood(graph, "w_likelihood");
        Normal direct = likelihood(graph, "direct_likelihood");
        OffsetValue shared = assertInstanceOf(OffsetValue.class, first.meanInput.get());
        OffsetValue other = assertInstanceOf(OffsetValue.class, third.meanInput.get());
        assertSame(shared, second.meanInput.get());
        assertNotSame(shared, other);
        assertSame(x, shared.xInput.get());
        assertSame(x, other.xInput.get());
        assertSame(x, direct.meanInput.get());
        assertEquals(1.0, shared.offsetInput.get(), 0.0);
        assertEquals(2.0, other.offsetInput.get(), 0.0);
        assertSame(Real.INSTANCE, shared.getDomain());
        assertSame(Real.INSTANCE, other.getDomain());
        assertTrue(x.getOutputs().containsAll(List.of(shared, other, direct, prior)));
        assertTrue(shared.getOutputs().containsAll(List.of(first, second)));
        assertTrue(other.getOutputs().contains(third));
        assertEquals(2, graph.calculationNodes.size());
        assertTrue(graph.calculationNodes.keySet().containsAll(List.of(shared, other)));
        assertEquals(4, graph.likelihoodDistributions.size());

        List<Normal> likelihoods = List.of(first, second, third, direct);
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

        // Controlled proposals isolate dependency and restoration checks from proposal randomness.
        double[] proposals = {-1.0, 0.0, 1.0, 0.5};
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
            assertEquals(proposals[step] + 1.0, shared.get(), 0.0);
            assertEquals(proposals[step] + 2.0, other.get(), 0.0);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertTrue(shared.somethingIsDirty());
            assertTrue(other.somethingIsDirty());

            double expectedPrior = normalLogDensity(proposals[step], 0.0, 1.0);
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
            assertEquals(currentX + 1.0, shared.get(), 0.0);
            assertEquals(currentX + 2.0, other.get(), 0.0);
            assertEquals("x", x.getID());
            assertSame(x, prior.paramInput.get());
            assertSame(x, shared.xInput.get());
            assertSame(x, other.xInput.get());
            assertEquals(2.0, first.paramInput.get().get(), 0.0);
            assertEquals(-1.0, second.paramInput.get().get(), 0.0);
            assertEquals(4.0, third.paramInput.get().get(), 0.0);
            assertEquals(-2.0, direct.paramInput.get().get(), 0.0);

            // Check the restored caches before recalculating densities could conceal a stale result.
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
            assertEquals(normalLogDensity(currentX, 0.0, 1.0), prior.calculateLogP(), TOLERANCE);
            assertEquals(expectedPosterior(currentX), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @CsvSource({"false, -2.0", "true, -2.0", "false, 0.0", "true, 0.0", "false, 2.0", "true, 2.0"})
    void eitherOperandOrderSupportsNegativeZeroAndPositiveOffsets(boolean constantFirst, double offset)
            throws Exception {
        String expression = constantFirst ? offset + " + x" : "x + " + offset;
        BEASTState graph = build("Real x ~ Normal(mean=0.0, sd=1.0)\n" + "Real y ~ Normal(mean=" + expression
                + ", sd=1.0) observed as 3.0\n");
        RealScalarParam<?> x = parameter(graph, "x");
        Normal prior = assertOriginalDraw(graph, x, 0.0, 1.0);
        Normal likelihood = likelihood(graph, "y_likelihood");
        OffsetValue shifted = assertInstanceOf(OffsetValue.class, likelihood.meanInput.get());
        assertSame(x, shifted.xInput.get());
        assertEquals(offset, shifted.offsetInput.get(), 0.0);
        assertEquals(1, graph.calculationNodes.size());
        assertTrue(graph.calculationNodes.containsKey(shifted));
        for (double value : new double[] {-1.0, 0.0, 2.0}) {
            x.set(value);
            assertEquals(value + offset, shifted.get(), 0.0);
            assertEquals(normalLogDensity(3.0, value + offset, 1.0), likelihood.calculateLogP(), TOLERANCE);
            assertEquals(normalLogDensity(value, 0.0, 1.0), prior.calculateLogP(), TOLERANCE);
            assertEquals("x", x.getID());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"offset1.phylospec", "offset2.phylospec"})
    void existingOffsetExamplesKeepTheirOriginalPriorAndEvaluateTheSum(String fileName) throws Exception {
        Path source = Path.of("src/test/java/tiling/multiast", fileName);
        BEASTState graph = build(Files.readString(source));
        RealScalarParam<?> normal = parameter(graph, "normal");
        Normal prior = assertOriginalDraw(graph, normal, 1.0, 2.0);
        RPNcalculator sum = rpn(graph, "z");
        assertTrue(sum.parametersInput.get().contains(normal));
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertEquals(1, graph.calculationNodes.size());
        for (double value : new double[] {-1.0, 0.0, 2.0}) {
            normal.set(value);
            assertEquals(value + 10.0, sum.getStatisticValue(0), TOLERANCE);
            assertEquals(normalLogDensity(value, 1.0, 2.0), prior.calculateLogP(), TOLERANCE);
            assertEquals("normal", normal.getID());
        }
    }

    @Test
    void subtractionMultiplicationAndDivisionKeepTheirExistingRpnPaths() throws Exception {
        BEASTState graph = build("""
                Real x ~ Normal(mean=0.0, sd=1.0)
                Real difference = x - 2.0
                Real product = x * 2.0
                Real quotient = x / 2.0
                """);
        RealScalarParam<?> x = parameter(graph, "x");
        Normal prior = assertOriginalDraw(graph, x, 0.0, 1.0);
        RPNcalculator difference = rpn(graph, "difference");
        RPNcalculator product = rpn(graph, "product");
        RPNcalculator quotient = rpn(graph, "quotient");
        assertEquals(3, graph.calculationNodes.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        for (double value : new double[] {-1.0, 0.0, 2.0}) {
            x.set(value);
            assertEquals(value - 2.0, difference.getStatisticValue(0), TOLERANCE);
            assertEquals(value * 2.0, product.getStatisticValue(0), TOLERANCE);
            assertEquals(value / 2.0, quotient.getStatisticValue(0), TOLERANCE);
            assertEquals(normalLogDensity(value, 0.0, 1.0), prior.calculateLogP(), TOLERANCE);
            assertEquals("x", x.getID());
        }
    }

    private static Normal assertOriginalDraw(BEASTState graph, RealScalarParam<?> value, double mean, double sd) {
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.operators.size());
        assertSame(Real.INSTANCE, value.getDomain());
        assertEquals(0.1, value.get(), 0.0, "The parent's existing initialization must be preserved.");
        Normal prior = assertInstanceOf(Normal.class, graph.priorDistributions.get(value));
        assertSame(value, prior.paramInput.get());
        assertEquals(mean, prior.meanInput.get().get(), 0.0);
        assertEquals(sd, prior.sdInput.get().get(), 0.0);
        assertEquals(value.getID() + "_prior", prior.getID());
        RealRandomWalkOperator operator = assertInstanceOf(RealRandomWalkOperator.class, graph.operators.getFirst());
        assertSame(value, operator.scalarInput.get());
        assertEquals(List.of(value), operator.listStateNodes());
        assertEquals(5.0, operator.m_pWeight.get(), 0.0);
        return prior;
    }

    private static double[] expectedLikelihoods(double x) {
        return new double[] {
            normalLogDensity(2.0, x + 1.0, 1.0),
            normalLogDensity(-1.0, x + 1.0, 1.0),
            normalLogDensity(4.0, x + 2.0, 1.0),
            normalLogDensity(-2.0, x, 1.0)
        };
    }

    private static double expectedPosterior(double x) {
        double result = normalLogDensity(x, 0.0, 1.0);
        for (double likelihood : expectedLikelihoods(x)) result += likelihood;
        return result;
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double residual = (value - mean) / sd;
        return -0.5 * Math.log(2.0 * Math.PI) - Math.log(sd) - 0.5 * residual * residual;
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("offset-test"));
        graph.initializeBEASTObjects();
        return graph;
    }
}
