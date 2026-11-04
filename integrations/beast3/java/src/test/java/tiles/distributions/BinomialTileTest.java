package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import operators.IntScalarUniformOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.BinomialDistribution;

class BinomialTileTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({"0, 0.0, 0", "0, 0.4, 0", "0, 1.0, 0", "6, 0.0, 0", "6, 1.0, 6", "6, 0.4, 2", "3, 0.5, 2"})
    void sampledCountStartsWithPositiveMassAndFixedBounds(int trials, double probability, int initialCount)
            throws Exception {
        BEASTState graph =
                build("NonNegativeInteger count ~ Binomial(numTrials=" + trials + ", p=" + probability + ")");
        IntScalarParam<?> count = count(graph);
        BinomialDistribution prior = assertInstanceOf(BinomialDistribution.class, graph.priorDistributions.get(count));

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(count, prior.paramInput.get());
        assertSame(NonNegativeInt.INSTANCE, count.getDomain());
        assertEquals(trials, prior.numTrialsInput.get());
        assertEquals(probability, prior.pInput.get().get(), 0.0);
        assertEquals(initialCount, count.get());
        assertEquals(0, count.getLower());
        assertEquals(trials, count.getUpper());
        assertEquals(binomialLogProbability(trials, initialCount, probability), prior.calculateLogP(), TOLERANCE);
        assertTrue(Double.isFinite(prior.getCurrentLogP()));

        Operator operator = operator(graph, count);
        assertInstanceOf(IntScalarUniformOperator.class, operator);
        assertEquals(List.of(count), operator.listStateNodes());
        assertEquals(1, graph.operators.size());
        if (trials == 0) {
            assertEquals(Double.NEGATIVE_INFINITY, operator.proposal());
            assertEquals(0, count.get());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void integerProbabilityEndpointsSupportSamplingDirectlyAndThroughNamedConstants(int probability) throws Exception {
        for (boolean named : new boolean[] {false, true}) {
            String declarations = named ? "Probability endpoint = " + probability + "\n" : "";
            String input = named ? "endpoint" : Integer.toString(probability);
            BEASTState graph =
                    build(declarations + "NonNegativeInteger count ~ Binomial(numTrials=6, p=" + input + ")");
            IntScalarParam<?> count = count(graph);
            BinomialDistribution prior =
                    assertInstanceOf(BinomialDistribution.class, graph.priorDistributions.get(count));

            assertEquals(1, graph.stateNodes.size());
            assertEquals(1, graph.priorDistributions.size());
            assertTrue(graph.likelihoodDistributions.isEmpty());
            assertSame(UnitInterval.INSTANCE, prior.pInput.get().getDomain());
            assertEquals((double) probability, prior.pInput.get().get(), 0.0);
            assertSame(count, prior.paramInput.get());
            assertEquals(6 * probability, count.get());
            assertEquals(0, count.getLower());
            assertEquals(6, count.getUpper());
            assertEquals(0.0, prior.calculateLogP(), 0.0);
            assertEquals(List.of(6 * probability), prior.sample());
            assertEquals(1, graph.operators.size());
            assertInstanceOf(IntScalarUniformOperator.class, operator(graph, count));
        }
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 2", "1, 6", "1, 2"})
    void integerProbabilityEndpointsPreservePossibleAndImpossibleObservations(int probability, int observedCount)
            throws Exception {
        for (boolean named : new boolean[] {false, true}) {
            String declarations = named ? "Probability endpoint = " + probability + "\n" : "";
            String input = named ? "endpoint" : Integer.toString(probability);
            BEASTState graph = build(declarations + "NonNegativeInteger count ~ Binomial(numTrials=6, p=" + input
                    + ") observed as " + observedCount);
            assertTrue(graph.stateNodes.isEmpty());
            assertTrue(graph.priorDistributions.isEmpty());
            assertTrue(graph.operators.isEmpty());
            assertEquals(1, graph.likelihoodDistributions.size());
            BinomialDistribution likelihood =
                    assertInstanceOf(BinomialDistribution.class, graph.likelihoodDistributions.getFirst());
            assertSame(UnitInterval.INSTANCE, likelihood.pInput.get().getDomain());
            assertEquals((double) probability, likelihood.pInput.get().get(), 0.0);
            assertEquals(observedCount, likelihood.paramInput.get().get());
            assertEquals(binomialLogProbability(6, observedCount, probability), likelihood.calculateLogP(), TOLERANCE);
        }
    }

    private static Stream<Arguments> observations() {
        return Stream.of(
                Arguments.of("", "0", 0, 0.4, 0),
                Arguments.of("", "1", 0, 0.4, 1),
                Arguments.of("", "0", 6, 0.0, 0),
                Arguments.of("", "2", 6, 0.0, 2),
                Arguments.of("", "6", 6, 1.0, 6),
                Arguments.of("", "2", 6, 1.0, 2),
                Arguments.of("", "2", 6, 0.4, 2),
                Arguments.of("", "7", 6, 0.4, 7),
                Arguments.of("NonNegativeInteger fixed = 7\n", "fixed", 6, 0.4, 7));
    }

    @ParameterizedTest
    @MethodSource("observations")
    void observationsPreserveValuesIncludingImpossibleCounts(
            String declarations, String observation, int trials, double probability, int expectedCount)
            throws Exception {
        BEASTState graph = build(declarations + "NonNegativeInteger count ~ Binomial(numTrials=" + trials + ", p="
                + probability + ") observed as " + observation);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        BinomialDistribution likelihood =
                assertInstanceOf(BinomialDistribution.class, graph.likelihoodDistributions.getFirst());
        assertEquals(expectedCount, likelihood.paramInput.get().get());
        // A positive literal can retain its narrower PositiveInt domain.
        assertInstanceOf(NonNegativeInt.class, likelihood.paramInput.get().getDomain());
        assertEquals(binomialLogProbability(trials, expectedCount, probability), likelihood.calculateLogP(), TOLERANCE);
    }

    private static Stream<Arguments> trialInputs() {
        return Stream.of(false, true)
                .flatMap(observed -> Stream.of(
                        Arguments.of("", "6", observed),
                        Arguments.of("NonNegativeInteger trials = 6\n", "trials", observed),
                        Arguments.of("", "2 * 3", observed),
                        Arguments.of(
                                "Vector<Integer> values = range(start=1, end=6)\n", "num(vector=values)", observed)));
    }

    @ParameterizedTest
    @MethodSource("trialInputs")
    void literalNamedFoldedAndComputedTrialsSupportSamplingAndObservation(
            String declarations, String trials, boolean observed) throws Exception {
        BEASTState graph = build(declarations + "Probability probability = 0.4\n"
                + "NonNegativeInteger count ~ Binomial(numTrials=" + trials + ", p=probability)"
                + (observed ? " observed as 2" : ""));
        BinomialDistribution distribution = assertInstanceOf(
                BinomialDistribution.class,
                observed ? graph.likelihoodDistributions.getFirst() : graph.priorDistributions.get(count(graph)));
        assertEquals(6, distribution.numTrialsInput.get());
        assertEquals(2, distribution.paramInput.get().get());
        assertEquals(binomialLogProbability(6, 2, 0.4), distribution.calculateLogP(), TOLERANCE);
        assertEquals(observed ? 0 : 1, graph.stateNodes.size());
        assertEquals(observed ? 0 : 1, graph.operators.size());
    }

    @Test
    void rejectsStochasticTrialCount() {
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> build("""
                NonNegativeInteger trials ~ Poisson(rate=4.0)
                NonNegativeInteger count ~ Binomial(numTrials=trials, p=0.4)
                """));
        assertTrue(error.getMessage().contains("numTrials"), error.getMessage());
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2 - 3"})
    void coreRejectsNegativeLiteralTrials(String trials) {
        assertThrows(
                TypeError.class, () -> build("NonNegativeInteger count ~ Binomial(numTrials=" + trials + ", p=0.4)"));
    }

    @Test
    void rejectsNegativeTrialValueAtTheTileBoundary() {
        BinomialTile tile = new BinomialTile();
        tile.numTrialsInput.setTile(
                valueTile(new IntScalarParam<>(-1, Int.INSTANCE), new TypeToken<IntScalarParam<Int>>() {}));
        tile.pInput.setTile(valueTile(
                new RealScalarParam<>(0.4, UnitInterval.INSTANCE), new TypeToken<RealScalarParam<UnitInterval>>() {}));
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> tile.applyTile(new BEASTState("negative-trials"), new IdentityHashMap<>()));
        assertTrue(error.getMessage().contains("numTrials"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void liveBetaProbabilityRemainsSharedThroughAcceptanceAndRejection(boolean acceptFirst) throws Exception {
        BEASTState graph = build("""
                Probability probability ~ Beta(alpha=2.0, beta=2.0)
                NonNegativeInteger first ~ Binomial(numTrials=5, p=probability) observed as 1
                NonNegativeInteger second ~ Binomial(numTrials=7, p=probability) observed as 4
                """);
        RealScalarParam<?> probability = probability(graph);
        Beta prior = assertInstanceOf(Beta.class, graph.priorDistributions.get(probability));
        BinomialDistribution first = observation(graph, 1);
        BinomialDistribution second = observation(graph, 4);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(1, graph.operators.size());
        assertEquals(List.of(probability), graph.operators.getFirst().listStateNodes());
        assertSame(UnitInterval.INSTANCE, probability.getDomain());
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
            double expectedFirst = binomialLogProbability(5, 1, probability.get());
            double expectedSecond = binomialLogProbability(7, 4, probability.get());
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedFirst + expectedSecond, proposedPosterior, TOLERANCE);
            assertNotEquals(previousPosterior, proposedPosterior, TOLERANCE);

            finishProposal(state, accept);
            assertEquals(accept ? proposals[step] : previousProbability, probability.get(), 0.0);
            assertEquals(1, first.paramInput.get().get());
            assertEquals(4, second.paramInput.get().get());
            assertSame(probability, first.pInput.get());
            assertSame(probability, second.pInput.get());
            // Inspect every cache before a fresh probability evaluation can hide a restore defect.
            assertEquals(accept ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedFirst : previousFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedSecond : previousSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(sharedLogDensity(probability.get()), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualCountOperatorMovesAndRestoresRejectedStates(boolean acceptFirst) throws Exception {
        BEASTState graph = build("NonNegativeInteger count ~ Binomial(numTrials=6, p=0.4)");
        IntScalarParam<?> count = count(graph);
        BinomialDistribution prior = assertInstanceOf(BinomialDistribution.class, graph.priorDistributions.get(count));
        Operator operator = operator(graph, count);
        CompoundDistribution posterior = posterior(prior);
        State state = state(graph, posterior);
        assertEquals(binomialLogProbability(6, 2, 0.4), state.robustlyCalcPosterior(posterior), TOLERANCE);
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
                assertEquals(0.0, operator.proposal(), 0.0);
                assertTrue(count.get() >= 0 && count.get() <= 6);
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                if (count.get() != previousCount) {
                    moved = true;
                    break;
                }
                finishProposal(state, false);
            }
            assertTrue(moved, "Exercise a changed count, not a self-proposal.");
            int proposedCount = count.get();
            double expected = binomialLogProbability(6, proposedCount, 0.4);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expected, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expected, proposedPosterior, TOLERANCE);

            finishProposal(state, accept);
            assertEquals(accept ? proposedCount : previousCount, count.get());
            assertEquals(accept ? expected : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(binomialLogProbability(6, count.get(), 0.4), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void rejectedProbabilityEndpointRestoresCountsBoundsAndCachedPosterior(double endpoint) throws Exception {
        BEASTState graph = build("""
                Probability probability ~ Beta(alpha=2.0, beta=2.0)
                NonNegativeInteger count ~ Binomial(numTrials=6, p=probability)
                """);
        RealScalarParam<?> probability = probability(graph);
        IntScalarParam<?> count = count(graph);
        Beta probabilityPrior = assertInstanceOf(Beta.class, graph.priorDistributions.get(probability));
        BinomialDistribution countPrior =
                assertInstanceOf(BinomialDistribution.class, graph.priorDistributions.get(count));
        CompoundDistribution posterior = posterior(countPrior, probabilityPrior);
        State state = state(graph, posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double previousProbability = probability.get();
        int previousCount = count.get();
        double previousCountPrior = countPrior.getCurrentLogP();
        double previousProbabilityPrior = probabilityPrior.getCurrentLogP();
        assertTrue(Double.isFinite(previousPosterior));
        assertTrue(previousCount > 0 && previousCount < 6);

        state.store(1);
        probability.set(endpoint);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(0, count.getLower());
        assertEquals(6, count.getUpper());
        assertEquals(previousCount, count.get());
        // Evaluate both factors explicitly: CompoundDistribution can stop at the first -Infinity.
        assertEquals(Double.NEGATIVE_INFINITY, countPrior.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, probabilityPrior.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());

        finishProposal(state, false);
        assertEquals(previousProbability, probability.get(), 0.0);
        assertEquals(previousCount, count.get());
        assertEquals(0, count.getLower());
        assertEquals(6, count.getUpper());
        assertSame(probability, countPrior.pInput.get());
        assertEquals(previousCountPrior, countPrior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousProbabilityPrior, probabilityPrior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shortSeededChainMovesCountsOrProbabilityForObservedCounts(boolean observed, @TempDir Path directory)
            throws Exception {
        Path prefix = directory.resolve(observed ? "binomial-probability" : "binomial-count");
        String model = observed
                ? "Probability probability ~ Beta(alpha=2.0, beta=2.0)\n"
                        + "NonNegativeInteger first ~ Binomial(numTrials=5, p=probability) observed as 1\n"
                        + "NonNegativeInteger second ~ Binomial(numTrials=7, p=probability) observed as 4\n"
                : "NonNegativeInteger count ~ Binomial(numTrials=6, p=0.4)\n";
        String source = model + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 81629
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("binomial-short-chain");
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
                assertTrue(value >= 0.0 && value <= 6.0);
            }
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            visited.add(value);
        }
        assertTrue(visited.size() > 1, "The default operator must update the sampled parameter.");
        assertEquals("200", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static double binomialLogProbability(int trials, int count, double probability) {
        if (count < 0 || count > trials) {
            return Double.NEGATIVE_INFINITY;
        }
        if (probability == 0.0) {
            return count == 0 ? 0.0 : Double.NEGATIVE_INFINITY;
        }
        if (probability == 1.0) {
            return count == trials ? 0.0 : Double.NEGATIVE_INFINITY;
        }
        double logChoose = 0.0;
        for (int i = 1; i <= count; i++) {
            logChoose += Math.log(trials - count + i) - Math.log(i);
        }
        return logChoose + count * Math.log(probability) + (trials - count) * Math.log1p(-probability);
    }

    private static double betaLogDensity(double probability) {
        // Beta(2,2): Gamma(4) / Gamma(2)^2 = 6.
        return Math.log(6.0) + Math.log(probability) + Math.log1p(-probability);
    }

    private static double sharedLogDensity(double probability) {
        return betaLogDensity(probability)
                + binomialLogProbability(5, 1, probability)
                + binomialLogProbability(7, 4, probability);
    }

    private static BinomialDistribution observation(BEASTState graph, int count) {
        return graph.likelihoodDistributions.stream()
                .map(distribution -> assertInstanceOf(BinomialDistribution.class, distribution))
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

    private static <T> Tile<T, BEASTState> valueTile(T value, TypeToken<?> type) {
        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {
            @Override
            public TypeToken<?> getTypeToken() {
                return type;
            }

            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("binomial-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
