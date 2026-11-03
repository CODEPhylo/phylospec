package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Bool;
import beast.base.spec.domain.Real;
import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.distribution.Beta;
import beast.base.spec.inference.operator.BitFlipOperator;
import beast.base.spec.inference.parameter.BoolScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.BernoulliDistribution;

class BernoulliTileTest {
    private static final double TOLERANCE = 1e-10;

    @ParameterizedTest
    @CsvSource({"0, false", "0.0, false", "0.2, false", "0.5, true", "0.8, true", "1.0, true", "1, true"})
    void sampledBooleanHasAValidInitialValueAndANativeBitFlip(String probability, boolean initialValue)
            throws Exception {
        BEASTState graph = build("Boolean outcome ~ Bernoulli(p=" + probability + ")");
        BoolScalarParam outcome = outcome(graph);
        BernoulliDistribution prior =
                assertInstanceOf(BernoulliDistribution.class, graph.priorDistributions.get(outcome));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertSame(outcome, prior.paramInput.get());
        assertSame(Bool.INSTANCE, outcome.getDomain());
        assertEquals(initialValue, outcome.get());
        assertEquals(logMass(initialValue, Double.parseDouble(probability)), prior.calculateLogP(), TOLERANCE);
        assertTrue(Double.isFinite(prior.getCurrentLogP()));
        BitFlipOperator operator = assertInstanceOf(BitFlipOperator.class, graph.operators.getFirst());
        assertSame(outcome, operator.parameterInput.get());
        assertEquals(List.of(outcome), operator.listStateNodes());
        assertEquals(1, graph.operators.size());
    }

    @ParameterizedTest
    @CsvSource({"0.0, false", "0.0, true", "0.3, false", "0.3, true", "1.0, false", "1.0, true"})
    void literalAndNamedObservationsRemainBooleanIncludingImpossibleOutcomes(double probability, boolean value)
            throws Exception {
        for (boolean named : new boolean[] {false, true}) {
            String source = (named ? "Boolean fixed = " + value + "\n" : "")
                    + "Boolean outcome ~ Bernoulli(p=" + probability + ") observed as "
                    + (named ? "fixed" : value);
            BEASTState graph = build(source);
            assertTrue(graph.stateNodes.isEmpty());
            assertTrue(graph.priorDistributions.isEmpty());
            assertTrue(graph.operators.isEmpty());
            assertEquals(1, graph.likelihoodDistributions.size());
            BernoulliDistribution likelihood =
                    assertInstanceOf(BernoulliDistribution.class, graph.likelihoodDistributions.getFirst());
            assertInstanceOf(BoolScalarParam.class, likelihood.paramInput.get());
            assertSame(Bool.INSTANCE, likelihood.paramInput.get().getDomain());
            assertEquals(value, likelihood.paramInput.get().get());
            assertEquals(logMass(value, probability), likelihood.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Real", "NonNegativeReal", "PositiveReal", "Probability"})
    void acceptsCoreRealAndNarrowerNamedProbabilityDomains(String type) throws Exception {
        BEASTState graph =
                build(type + " probability = 0.3\n" + "Boolean outcome ~ Bernoulli(p=probability) observed as true");
        BernoulliDistribution likelihood =
                assertInstanceOf(BernoulliDistribution.class, graph.likelihoodDistributions.getFirst());
        assertEquals(0.3, likelihood.pInput.get().get(), 0.0);
        assertEquals(Math.log(0.3), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "1.1"})
    void invalidInitialProbabilityFailsClearlyWithoutChangingTheCoreRealType(String probability) {
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> build("Real probability = " + probability + "\nBoolean outcome ~ Bernoulli(p=probability)"));
        assertTrue(error.getMessage().contains("Bernoulli"), error.getMessage());
        assertTrue(error.getMessage().contains("[0, 1]"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedProbabilityUpdatesLikelihoodAndPosteriorAcrossAcceptanceAndRejection(boolean acceptFirst)
            throws Exception {
        BEASTState graph = build("""
                Probability probability ~ Beta(alpha=2.0, beta=2.0)
                Boolean first ~ Bernoulli(p=probability) observed as true
                Boolean second ~ Bernoulli(p=probability) observed as false
                """);
        RealScalarParam<?> probability = probability(graph);
        Beta prior = assertInstanceOf(Beta.class, graph.priorDistributions.get(probability));
        BernoulliDistribution first = observation(graph, true);
        BernoulliDistribution second = observation(graph, false);
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
            double expectedFirst = logMass(true, probability.get());
            double expectedSecond = logMass(false, probability.get());
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedFirst + expectedSecond, proposedPosterior, TOLERANCE);
            assertNotEquals(previousPosterior, proposedPosterior, TOLERANCE);

            finishProposal(state, accept);
            assertEquals(accept ? proposals[step] : previousProbability, probability.get(), 0.0);
            assertTrue(first.paramInput.get().get());
            assertFalse(second.paramInput.get().get());
            assertSame(probability, first.pInput.get());
            assertSame(probability, second.pInput.get());
            // Check restored caches before a new calculation could hide a restore error.
            assertEquals(accept ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedFirst : previousFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedSecond : previousSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(sharedLogDensity(probability.get()), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.2, 1.2})
    void outOfRangeRealProbabilityIsRejectedWithoutClampingOrAbortingAndRestoresState(double invalid) throws Exception {
        BEASTState graph = build("""
                Real probability ~ Uniform(lower=-1.0, upper=2.0)
                Boolean first ~ Bernoulli(p=probability) observed as true
                Boolean second ~ Bernoulli(p=probability) observed as false
                """);
        RealScalarParam<?> probability = probability(graph);
        assertSame(Real.INSTANCE, probability.getDomain());
        assertEquals(-1.0, probability.getLower(), 0.0);
        assertEquals(2.0, probability.getUpper(), 0.0);
        Distribution prior = graph.priorDistributions.get(probability);
        BernoulliDistribution first = observation(graph, true);
        BernoulliDistribution second = observation(graph, false);
        CompoundDistribution posterior = posterior(prior, first, second);
        State state = state(graph, posterior);
        double previousPosterior = state.robustlyCalcPosterior(posterior);
        double previousProbability = probability.get();
        double previousPrior = prior.getCurrentLogP();
        double previousFirst = first.getCurrentLogP();
        double previousSecond = second.getCurrentLogP();
        assertTrue(Double.isFinite(previousPosterior));

        state.store(1);
        probability.set(invalid);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(invalid, probability.get(), 0.0);
        assertEquals(-Math.log(3.0), prior.calculateLogP(), TOLERANCE);
        // Evaluate both outcomes: a naive formula can give a positive logP for one of them.
        assertEquals(Double.NEGATIVE_INFINITY, first.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, second.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());
        finishProposal(state, false);

        assertEquals(previousProbability, probability.get(), 0.0);
        assertEquals(previousPrior, prior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousFirst, first.getCurrentLogP(), TOLERANCE);
        assertEquals(previousSecond, second.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
        assertEquals(previousPosterior, posterior.calculateLogP(), TOLERANCE);
        assertEquals(-1.0, probability.getLower(), 0.0);
        assertEquals(2.0, probability.getUpper(), 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nativeBitFlipMovesTheBooleanAndRestoresRejectedValuesAndDensities(boolean acceptFirst) throws Exception {
        BEASTState graph = build("Boolean outcome ~ Bernoulli(p=0.3)");
        BoolScalarParam outcome = outcome(graph);
        BernoulliDistribution prior =
                assertInstanceOf(BernoulliDistribution.class, graph.priorDistributions.get(outcome));
        Operator operator = graph.operators.getFirst();
        assertInstanceOf(BitFlipOperator.class, operator);
        CompoundDistribution posterior = posterior(prior);
        State state = state(graph, posterior);
        assertEquals(logMass(false, 0.3), state.robustlyCalcPosterior(posterior), TOLERANCE);
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            boolean previousValue = outcome.get();
            double previousPrior = prior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            assertEquals(0.0, operator.proposal(), 0.0);
            assertEquals(!previousValue, outcome.get());
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double expected = logMass(!previousValue, 0.3);
            assertEquals(expected, posterior.calculateLogP(), TOLERANCE);
            assertEquals(expected, prior.getCurrentLogP(), TOLERANCE);

            finishProposal(state, accept);
            assertEquals(accept ? !previousValue : previousValue, outcome.get());
            assertEquals(accept ? expected : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expected : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(logMass(outcome.get(), 0.3), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void endpointPriorRejectsTheImpossibleFlipAndRestoresItsBoolean(double probability) throws Exception {
        BEASTState graph = build("Boolean outcome ~ Bernoulli(p=" + probability + ")");
        BoolScalarParam outcome = outcome(graph);
        Distribution prior = graph.priorDistributions.get(outcome);
        CompoundDistribution posterior = posterior(prior);
        State state = state(graph, posterior);
        assertEquals(0.0, state.robustlyCalcPosterior(posterior), 0.0);
        boolean previousValue = outcome.get();
        state.store(1);
        assertEquals(0.0, graph.operators.getFirst().proposal(), 0.0);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(!previousValue, outcome.get());
        assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());
        finishProposal(state, false);
        assertEquals(previousValue, outcome.get());
        assertEquals(0.0, prior.getCurrentLogP(), 0.0);
        assertEquals(0.0, posterior.getCurrentLogP(), 0.0);
        assertEquals(0.0, posterior.calculateLogP(), 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shortSeededChainSamplesBooleansOrEstimatesProbabilityFromObservedBooleans(
            boolean observed, @TempDir Path directory) throws Exception {
        Path prefix = directory.resolve(observed ? "bernoulli-probability" : "bernoulli-outcome");
        String model = observed
                ? "Probability probability ~ Beta(alpha=2.0, beta=2.0)\n"
                        + "Boolean first ~ Bernoulli(p=probability) observed as true\n"
                        + "Boolean second ~ Bernoulli(p=probability) observed as false\n"
                : "Boolean outcome ~ Bernoulli(p=0.3)\n";
        String source = model + """
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 81629
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("bernoulli-short-chain");
        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int valueColumn = headers.indexOf(observed ? "probability" : "outcome");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(valueColumn >= 0 && posteriorColumn >= 0, headers.toString());
        assertEquals(42, lines.size(), "Header plus samples 0, 5, ..., 200.");
        Set<String> visited = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.trim().split("\\s+");
            String value = fields[valueColumn];
            if (observed) {
                double p = Double.parseDouble(value);
                assertTrue(p > 0.0 && p < 1.0);
            } else {
                assertTrue(Set.of("false", "true").contains(value), value);
            }
            assertTrue(Double.isFinite(Double.parseDouble(fields[posteriorColumn])));
            visited.add(value);
        }
        assertTrue(visited.size() > 1, "The default operator must move the sampled value.");
        assertEquals("200", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static double logMass(boolean value, double probability) {
        return value ? Math.log(probability) : Math.log1p(-probability);
    }

    private static double betaLogDensity(double probability) {
        return Math.log(6.0) + Math.log(probability) + Math.log1p(-probability);
    }

    private static double sharedLogDensity(double probability) {
        return betaLogDensity(probability) + logMass(true, probability) + logMass(false, probability);
    }

    private static BernoulliDistribution observation(BEASTState graph, boolean value) {
        return graph.likelihoodDistributions.stream()
                .map(distribution -> assertInstanceOf(BernoulliDistribution.class, distribution))
                .filter(distribution -> distribution.paramInput.get().get() == value)
                .findFirst()
                .orElseThrow();
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static BoolScalarParam outcome(BEASTState graph) {
        return assertInstanceOf(BoolScalarParam.class, node(graph, "outcome"));
    }

    private static RealScalarParam<?> probability(BEASTState graph) {
        return assertInstanceOf(RealScalarParam.class, node(graph, "probability"));
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("bernoulli-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
