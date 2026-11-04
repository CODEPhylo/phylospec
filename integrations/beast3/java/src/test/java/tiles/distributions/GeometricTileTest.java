package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import operators.IntScalarRandomWalkOperator;
import org.junit.jupiter.api.io.TempDir;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.GeometricDistribution;

class GeometricTileTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @ValueSource(strings = {"0.4", "1.0", "1"})
    void literalAndNamedProbabilitiesStartAtAValidCountWithStableBounds(String probability) throws Exception {
        for (boolean named : new boolean[] {false, true}) {
            BEASTState graph = build((named ? "Probability fixed = " + probability + "\n" : "")
                    + "NonNegativeInteger count ~ Geometric(p=" + (named ? "fixed" : probability) + ")");
            IntScalarParam<?> count = count(graph);
            GeometricDistribution prior =
                    assertInstanceOf(GeometricDistribution.class, graph.priorDistributions.get(count));
            assertEquals(1, graph.stateNodes.size());
            assertEquals(1, graph.priorDistributions.size());
            assertTrue(graph.likelihoodDistributions.isEmpty());
            assertSame(count, prior.paramInput.get());
            assertSame(NonNegativeInt.INSTANCE, count.getDomain());
            assertEquals(0, count.get());
            assertEquals(0, count.getLower());
            assertEquals(Integer.MAX_VALUE, count.getUpper());
            assertEquals(Double.parseDouble(probability), prior.pInput.get().get(), 0.0);
            assertSame(UnitInterval.INSTANCE, prior.pInput.get().getDomain());
            assertEquals(logMass(0, Double.parseDouble(probability)), prior.calculateLogP(), TOLERANCE);
            Operator operator = operator(graph, count);
            assertInstanceOf(IntScalarRandomWalkOperator.class, operator);
            assertEquals(List.of(count), operator.listStateNodes());
            assertEquals(1, graph.operators.size());
        }
    }

    @ParameterizedTest
    @CsvSource({"0.4, 0", "0.4, 3", "1.0, 0", "1.0, 2", "1, 0", "1, 2"})
    void observationsKeepTheirValuesAndDoNotCreateSamplingState(String probability, int observed) throws Exception {
        for (boolean named : new boolean[] {false, true}) {
            BEASTState graph = build((named ? "Probability fixed = " + probability + "\n" : "")
                    + "NonNegativeInteger count ~ Geometric(p=" + (named ? "fixed" : probability)
                    + ") observed as " + observed);
            assertTrue(graph.stateNodes.isEmpty());
            assertTrue(graph.priorDistributions.isEmpty());
            assertTrue(graph.operators.isEmpty());
            assertEquals(1, graph.likelihoodDistributions.size());
            GeometricDistribution likelihood =
                    assertInstanceOf(GeometricDistribution.class, graph.likelihoodDistributions.getFirst());
            assertEquals(observed, likelihood.paramInput.get().get());
            assertInstanceOf(NonNegativeInt.class, likelihood.paramInput.get().getDomain());
            assertEquals(logMass(observed, Double.parseDouble(probability)), likelihood.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0"})
    void zeroInitialProbabilityIsDiagnosedForSamplingAndObservation(String probability) {
        for (boolean named : new boolean[] {false, true}) {
            for (boolean observed : new boolean[] {false, true}) {
                String source = (named ? "Probability fixed = " + probability + "\n" : "")
                        + "NonNegativeInteger count ~ Geometric(p=" + (named ? "fixed" : probability) + ")"
                        + (observed ? " observed as 0" : "");
                TileApplicationError error = assertThrows(TileApplicationError.class, () -> build(source));
                assertTrue(error.getMessage().contains("Geometric")
                        || error.getMessage().contains("geometric"));
                assertTrue(error.getMessage().contains("(0, 1]"), error.getMessage());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0.5"})
    void coreStillRejectsNegativeAndFractionalObservedCounts(String count) {
        assertThrows(TypeError.class, () -> build("NonNegativeInteger count ~ Geometric(p=0.4) observed as " + count));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedLiveProbabilityUpdatesBothLikelihoodsAndRestoresTheirCaches(boolean acceptFirst) throws Exception {
        String probabilitySource = "Probability probability ~ Beta(alpha=2.0, beta=2.0)\n";
        BEASTState baseline = build(probabilitySource);
        BEASTState graph = build(probabilitySource + """
                NonNegativeInteger first ~ Geometric(p=probability) observed as 0
                NonNegativeInteger second ~ Geometric(p=probability) observed as 3
                """);
        RealScalarParam<?> probability = probability(graph);
        Beta prior = assertInstanceOf(Beta.class, graph.priorDistributions.get(probability));
        GeometricDistribution first = observation(graph, 0);
        GeometricDistribution second = observation(graph, 3);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());
        assertEquals(List.of(probability), graph.operators.getFirst().listStateNodes());
        assertSame(probability, first.pInput.get());
        assertSame(probability, second.pInput.get());
        assertTrue(probability.getOutputs().contains(first));
        assertTrue(probability.getOutputs().contains(second));
        assertEquals(List.of("probability"), first.getConditions());
        assertEquals(List.of("probability"), second.getConditions());

        CompoundDistribution posterior = posterior(prior, first, second);
        State state = state(graph, posterior);
        assertEquals(sharedLogDensity(probability.get()), state.robustlyCalcPosterior(posterior), TOLERANCE);
        double[] proposals = {0.2, 0.7, 0.35};
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            double previousProbability = probability.get();
            double previousPrior = prior.getCurrentLogP();
            double previousFirst = first.getCurrentLogP();
            double previousSecond = second.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            probability.set(proposals[step]);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expectedPrior = betaLogDensity(probability.get());
            double expectedFirst = logMass(0, probability.get());
            double expectedSecond = logMass(3, probability.get());
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedFirst + expectedSecond, proposedPosterior, TOLERANCE);
            assertNotEquals(previousPosterior, proposedPosterior, TOLERANCE);

            finishProposal(state, accept);
            assertEquals(accept ? proposals[step] : previousProbability, probability.get(), 0.0);
            assertSame(probability, first.pInput.get());
            assertSame(probability, second.pInput.get());
            // Check the restored caches before recomputation could hide a restoration error.
            assertEquals(accept ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedFirst : previousFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedSecond : previousSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(sharedLogDensity(probability.get()), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualCountOperatorMovesAndRestoresAcceptedAndRejectedStates(boolean acceptFirst) throws Exception {
        BEASTState graph = build("NonNegativeInteger count ~ Geometric(p=0.4)");
        IntScalarParam<?> count = count(graph);
        GeometricDistribution prior =
                assertInstanceOf(GeometricDistribution.class, graph.priorDistributions.get(count));
        Operator operator = operator(graph, count);
        CompoundDistribution posterior = posterior(prior);
        State state = state(graph, posterior);
        assertEquals(logMass(0, 0.4), state.robustlyCalcPosterior(posterior), TOLERANCE);
        Randomizer.setSeed(94217);
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            int previousCount = count.get();
            double previousPrior = prior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            boolean moved = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                state.store(step * 100L + attempt);
                double logHastingsRatio = operator.proposal();
                if (logHastingsRatio == Double.NEGATIVE_INFINITY) {
                    assertEquals(previousCount, count.get());
                    state.restore();
                    state.setEverythingDirty(false);
                    continue;
                }
                assertEquals(0.0, logHastingsRatio, 0.0);
                assertEquals(1, Math.abs(count.get() - previousCount));
                assertTrue(count.get() >= 0);
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                moved = true;
                break;
            }
            assertTrue(moved, "Exercise an actual count change, including a move away from zero.");
            int proposedCount = count.get();
            double expected = logMass(proposedCount, 0.4);
            assertEquals(expected, posterior.calculateLogP(), TOLERANCE);
            assertEquals(expected, prior.getCurrentLogP(), TOLERANCE);
            finishProposal(state, accept);
            assertEquals(accept ? proposedCount : previousCount, count.get());
            assertEquals(accept ? expected : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expected : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(logMass(count.get(), 0.4), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void rejectedProbabilityEndpointsRestoreSampledCountsBoundsAndPosterior(double endpoint) throws Exception {
        BEASTState graph = build("""
                Probability probability ~ Beta(alpha=2.0, beta=2.0)
                NonNegativeInteger count ~ Geometric(p=probability)
                """);
        RealScalarParam<?> probability = probability(graph);
        IntScalarParam<?> count = count(graph);
        count.set(2);
        Distribution probabilityPrior = graph.priorDistributions.get(probability);
        GeometricDistribution countPrior =
                assertInstanceOf(GeometricDistribution.class, graph.priorDistributions.get(count));
        CompoundDistribution posterior = posterior(countPrior, probabilityPrior);
        State state = state(graph, posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double previousProbability = probability.get();
        double previousCountPrior = countPrior.getCurrentLogP();
        double previousProbabilityPrior = probabilityPrior.getCurrentLogP();
        assertTrue(Double.isFinite(previousPosterior));
        assertEquals(2, graph.operators.size());
        assertInstanceOf(IntScalarRandomWalkOperator.class, operator(graph, count));

        state.store(1);
        probability.set(endpoint);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(2, count.get());
        assertEquals(0, count.getLower());
        assertEquals(Integer.MAX_VALUE, count.getUpper());
        // Evaluate both factors because CompoundDistribution may stop at the first -Infinity.
        assertEquals(Double.NEGATIVE_INFINITY, countPrior.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, probabilityPrior.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());
        finishProposal(state, false);
        assertEquals(previousProbability, probability.get(), 0.0);
        assertEquals(2, count.get());
        assertEquals(0, count.getLower());
        assertEquals(Integer.MAX_VALUE, count.getUpper());
        assertSame(probability, countPrior.pInput.get());
        assertEquals(previousCountPrior, countPrior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousProbabilityPrior, probabilityPrior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shortSeededChainsMoveCountsOrEstimateProbabilityFromObservations(boolean observed, @TempDir Path directory)
            throws Exception {
        Path prefix = directory.resolve(observed ? "geometric-probability" : "geometric-count");
        String model = observed
                ? "Probability probability ~ Beta(alpha=2.0, beta=2.0)\n"
                        + "NonNegativeInteger first ~ Geometric(p=probability) observed as 0\n"
                        + "NonNegativeInteger second ~ Geometric(p=probability) observed as 3\n"
                : "NonNegativeInteger count ~ Geometric(p=0.4)\n";
        String source = model + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 81629
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("geometric-short-chain");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int valueColumn = headers.indexOf(observed ? "probability" : "count");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(valueColumn >= 0 && posteriorColumn >= 0, headers.toString());
        assertEquals(42, lines.size(), "Header plus samples 0, 5, ..., 200.");
        Set<Double> visited = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            double value = Double.parseDouble(fields[valueColumn]);
            assertTrue(Double.isFinite(value));
            if (observed) {
                assertTrue(value > 0.0 && value < 1.0);
            } else {
                assertEquals(Math.rint(value), value, 0.0);
                assertTrue(value >= 0.0);
            }
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            visited.add(value);
        }
        assertTrue(visited.size() > 1, "The configured operator must move the sampled parameter.");
        assertEquals("200", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static double logMass(int count, double probability) {
        if (probability <= 0.0 || probability > 1.0 || count < 0) return Double.NEGATIVE_INFINITY;
        if (probability == 1.0) return count == 0 ? 0.0 : Double.NEGATIVE_INFINITY;
        return Math.log(probability) + count * Math.log1p(-probability);
    }

    private static double betaLogDensity(double probability) {
        return Math.log(6.0) + Math.log(probability) + Math.log1p(-probability);
    }

    private static double sharedLogDensity(double probability) {
        return betaLogDensity(probability) + logMass(0, probability) + logMass(3, probability);
    }

    private static GeometricDistribution observation(BEASTState graph, int count) {
        return graph.likelihoodDistributions.stream()
                .map(distribution -> assertInstanceOf(GeometricDistribution.class, distribution))
                .filter(distribution -> distribution.paramInput.get().get() == count)
                .findFirst()
                .orElseThrow();
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static IntScalarParam<?> count(BEASTState graph) {
        return assertInstanceOf(IntScalarParam.class, node(graph, "count"));
    }

    private static RealScalarParam<?> probability(BEASTState graph) {
        return assertInstanceOf(RealScalarParam.class, node(graph, "probability"));
    }

    private static Operator operator(BEASTState graph, StateNode node) {
        List<Operator> operators = graph.operators.stream()
                .filter(operator -> operator.listStateNodes().contains(node))
                .toList();
        assertEquals(1, operators.size());
        return operators.getFirst();
    }

    private static CompoundDistribution posterior(Distribution... factors) {
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", List.of(factors));
        return posterior;
    }

    private static State state(BEASTState graph, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", List.copyOf(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static void finishProposal(State state, boolean accept) {
        if (accept) {
            state.acceptCalculationNodes();
        } else {
            state.restore();
            state.restoreCalculationNodes();
        }
        state.setEverythingDirty(false);
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("geometric-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
