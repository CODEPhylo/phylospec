package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.distribution.Dirichlet;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import operators.IntScalarUniformOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import runner.PhyloSpecRunner;
import tiling.CategoricalDistribution;

class CategoricalTileTest {
    private static final double TOLERANCE = 1e-12;

    private static Stream<Arguments> sampledConfigurations() {
        return Stream.of(
                Arguments.of("[0.2, 0.3, 0.5]", 3, 0.5),
                Arguments.of("[0.0, 1.0, 0.0]", 2, 1.0),
                Arguments.of("[0.5, 0.5]", 1, 0.5),
                Arguments.of("[1.0]", 1, 1.0));
    }

    @ParameterizedTest
    @MethodSource("sampledConfigurations")
    void sampledCategoryHasPositiveMassFiniteBoundsAndOneOperator(
            String probabilities, int initialCategory, double initialMass) throws Exception {
        BEASTState graph = build("Simplex probabilities = " + probabilities
                + "\nPositiveInteger category ~ Categorical(probabilities=probabilities)");
        IntScalarParam<?> category = category(graph);
        CategoricalDistribution prior =
                assertInstanceOf(CategoricalDistribution.class, graph.priorDistributions.get(category));

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.calculationNodes.isEmpty());
        assertSame(category, prior.paramInput.get());
        assertSame(PositiveInt.INSTANCE, category.getDomain());
        assertEquals(initialCategory, category.get());
        assertEquals(initialMass, prior.probabilitiesInput.get().get(category.get() - 1), 0.0);
        assertEquals(Math.log(initialMass), prior.calculateLogP(), TOLERANCE);
        assertEquals(1, category.getLower());
        assertEquals(prior.probabilitiesInput.get().size(), category.getUpper());

        Operator operator = operator(graph, category);
        assertInstanceOf(IntScalarUniformOperator.class, operator);
        assertEquals(List.of(category), operator.listStateNodes());
        assertEquals(1, graph.operators.size());
        if (prior.probabilitiesInput.get().size() == 1) {
            assertEquals(Double.NEGATIVE_INFINITY, operator.proposal());
            assertEquals(1, category.get());
        }
    }

    private static Stream<Arguments> observations() {
        return Stream.of(
                Arguments.of("", "1", 1, Math.log(0.2)),
                Arguments.of("", "2", 2, Double.NEGATIVE_INFINITY),
                Arguments.of("", "4", 4, Double.NEGATIVE_INFINITY),
                Arguments.of("PositiveInteger fixed = 4\n", "fixed", 4, Double.NEGATIVE_INFINITY));
    }

    @ParameterizedTest
    @MethodSource("observations")
    void observationsPreserveValuesIncludingZeroMassAndOutOfRangeCategories(
            String declarations, String observation, int expectedCategory, double expectedLogP) throws Exception {
        BEASTState graph = build(declarations + """
                Simplex probabilities = [0.2, 0.0, 0.8]
                PositiveInteger category ~ Categorical(probabilities=probabilities) observed as %s
                """.formatted(observation));

        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        CategoricalDistribution likelihood =
                assertInstanceOf(CategoricalDistribution.class, graph.likelihoodDistributions.getFirst());
        assertEquals(expectedCategory, likelihood.paramInput.get().get());
        assertSame(PositiveInt.INSTANCE, likelihood.paramInput.get().getDomain());
        assertEquals(expectedLogP, likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void liveDirichletProbabilitiesRemainSharedAcrossConsumersAndStateRestoration(boolean acceptFirst)
            throws Exception {
        BEASTState graph = build("""
                Simplex probabilities ~ Dirichlet(concentration=[2.0, 2.0, 2.0])
                PositiveInteger first ~ Categorical(probabilities=probabilities) observed as 1
                PositiveInteger second ~ Categorical(probabilities=probabilities) observed as 3
                """);
        SimplexParam probabilities = assertInstanceOf(SimplexParam.class, node(graph, "probabilities"));
        Dirichlet prior = assertInstanceOf(Dirichlet.class, graph.priorDistributions.get(probabilities));
        CategoricalDistribution first = observation(graph, 1);
        CategoricalDistribution second = observation(graph, 3);

        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(1, graph.operators.size());
        assertEquals(List.of(probabilities), graph.operators.getFirst().listStateNodes());
        assertSame(probabilities, first.probabilitiesInput.get());
        assertSame(probabilities, second.probabilitiesInput.get());
        assertTrue(probabilities.getOutputs().contains(first));
        assertTrue(probabilities.getOutputs().contains(second));
        assertEquals(List.of("probabilities"), first.getConditions());
        assertEquals(List.of("probabilities"), second.getConditions());

        CompoundDistribution posterior = posterior(prior, first, second);
        State state = state(graph, posterior);
        assertEquals(
                dirichletLogDensity(probabilities) + Math.log(probabilities.get(0)) + Math.log(probabilities.get(2)),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);

        double[][] proposals = {{0.2, 0.3, 0.5}, {0.5, 0.4, 0.1}, {0.1, 0.2, 0.7}};
        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            List<Double> previousProbabilities = List.copyOf(probabilities.getElements());
            double previousPrior = prior.getCurrentLogP();
            double previousFirst = first.getCurrentLogP();
            double previousSecond = second.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();

            state.store(step + 1);
            for (int index = 0; index < proposals[step].length; index++) {
                probabilities.set(index, proposals[step][index]);
            }
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();

            double expectedPrior = dirichletLogDensity(probabilities);
            double expectedFirst = Math.log(probabilities.get(0));
            double expectedSecond = Math.log(probabilities.get(2));
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedPrior + expectedFirst + expectedSecond, proposedPosterior, TOLERANCE);
            assertNotEquals(previousPosterior, proposedPosterior, TOLERANCE);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);

            List<Double> expectedProbabilities =
                    accept ? Arrays.stream(proposals[step]).boxed().toList() : previousProbabilities;
            assertEquals(expectedProbabilities, probabilities.getElements());
            assertSame(probabilities, first.probabilitiesInput.get());
            assertSame(probabilities, second.probabilitiesInput.get());
            // Inspect all native caches before recalculating any density.
            assertEquals(accept ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedFirst : previousFirst, first.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedSecond : previousSecond, second.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    dirichletLogDensity(probabilities)
                            + Math.log(probabilities.get(0))
                            + Math.log(probabilities.get(2)),
                    posterior.calculateLogP(),
                    TOLERANCE);
            assertEquals(Math.log(probabilities.get(0)), first.calculateLogP(), TOLERANCE);
            assertEquals(Math.log(probabilities.get(2)), second.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualCategoryOperatorMovesWithinSupportAndRestoresRejectedStates(boolean acceptFirst) throws Exception {
        BEASTState graph = build("""
                Simplex probabilities = [0.2, 0.3, 0.5]
                PositiveInteger category ~ Categorical(probabilities=probabilities)
                """);
        IntScalarParam<?> category = category(graph);
        CategoricalDistribution prior =
                assertInstanceOf(CategoricalDistribution.class, graph.priorDistributions.get(category));
        Operator operator = operator(graph, category);
        CompoundDistribution posterior = posterior(prior);
        State state = state(graph, posterior);
        assertEquals(Math.log(0.5), state.robustlyCalcPosterior(posterior), TOLERANCE);
        Randomizer.setSeed(4951);

        boolean[] decisions = {acceptFirst, !acceptFirst, acceptFirst};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            int previousCategory = category.get();
            double previousPrior = prior.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            boolean moved = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                state.store(step * 100L + attempt);
                assertEquals(0.0, operator.proposal(), 0.0);
                assertTrue(category.get() >= 1 && category.get() <= 3);
                state.storeCalculationNodes();
                state.checkCalculationNodesDirtiness();
                if (category.get() != previousCategory) {
                    moved = true;
                    break;
                }
                state.restore();
                state.restoreCalculationNodes();
                state.setEverythingDirty(false);
            }
            assertTrue(moved, "Exercise a changed category, not a self-proposal.");
            int proposedCategory = category.get();
            double expected = Math.log(new double[] {0.2, 0.3, 0.5}[proposedCategory - 1]);
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(expected, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expected, proposedPosterior, TOLERANCE);
            assertNotEquals(previousPrior, expected, TOLERANCE);

            if (accept) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);

            assertEquals(accept ? proposedCategory : previousCategory, category.get());
            // The parameter, prior, and compound posterior must already be restored here.
            assertEquals(accept ? expected : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    Math.log(new double[] {0.2, 0.3, 0.5}[category.get() - 1]), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void rejectedZeroMassCategoryRestoresTheFinitePosterior() throws Exception {
        BEASTState graph = build("""
                Simplex probabilities = [0.0, 1.0, 0.0]
                PositiveInteger category ~ Categorical(probabilities=probabilities)
                """);
        IntScalarParam<?> category = category(graph);
        CategoricalDistribution prior =
                assertInstanceOf(CategoricalDistribution.class, graph.priorDistributions.get(category));
        CompoundDistribution posterior = posterior(prior);
        State state = state(graph, posterior);
        assertEquals(0.0, state.robustlyCalcPosterior(posterior), 0.0);
        assertEquals(2, category.get());

        state.store(1);
        category.set(1);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertEquals(Double.NEGATIVE_INFINITY, posterior.calculateLogP());
        assertEquals(Double.NEGATIVE_INFINITY, prior.getCurrentLogP());

        state.restore();
        state.restoreCalculationNodes();
        state.setEverythingDirty(false);
        assertEquals(2, category.get());
        // Check restored probabilities before any fresh likelihood evaluation.
        assertEquals(0.0, prior.getCurrentLogP(), 0.0);
        assertEquals(0.0, posterior.getCurrentLogP(), 0.0);
        assertEquals(0.0, posterior.calculateLogP(), 0.0);
    }

    @Test
    void shortSeededChainLogsValidMovingCategories(@TempDir Path directory) throws Exception {
        Path prefix = directory.resolve("categorical");
        String source = """
                Simplex probabilities = [0.2, 0.3, 0.5]
                PositiveInteger category ~ Categorical(probabilities=probabilities)
                mcmc {
                    Integer chainLength = 200
                    Integer randomSeed = 61027
                    Integer defaultLogEvery = 5
                    String outputPrefix = "%s"
                }
                """.formatted(prefix.toString().replace("\\", "\\\\"));
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec("categorical-short-chain");

        List<String> lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        int categoryColumn = headers.indexOf("category");
        int posteriorColumn = headers.indexOf("posterior");
        assertTrue(categoryColumn >= 0 && posteriorColumn >= 0, headers.toString());
        assertEquals(42, lines.size(), "Header plus samples 0, 5, ..., 200.");
        Set<Integer> visited = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] values = line.trim().split("\\s+");
            int category = Integer.parseInt(values[categoryColumn]);
            assertTrue(category >= 1 && category <= 3);
            assertTrue(Double.isFinite(Double.parseDouble(values[posteriorColumn])));
            visited.add(category);
        }
        assertTrue(visited.size() > 1, "The default operator must move the category.");
        assertEquals("200", lines.getLast().trim().split("\\s+")[0]);
        assertTrue(Files.size(Path.of(prefix + ".state.xml")) > 0);
    }

    private static double dirichletLogDensity(SimplexParam probabilities) {
        // Dirichlet(2,2,2): Gamma(6) / Gamma(2)^3 = 120.
        return Math.log(120.0)
                + Math.log(probabilities.get(0))
                + Math.log(probabilities.get(1))
                + Math.log(probabilities.get(2));
    }

    private static CategoricalDistribution observation(BEASTState graph, int category) {
        return graph.likelihoodDistributions.stream()
                .map(distribution -> assertInstanceOf(CategoricalDistribution.class, distribution))
                .filter(distribution -> distribution.paramInput.get().get() == category)
                .findFirst()
                .orElseThrow();
    }

    private static StateNode node(BEASTState graph, String id) {
        return graph.stateNodes.keySet().stream()
                .filter(node -> id.equals(node.getID()))
                .findFirst()
                .orElseThrow();
    }

    private static IntScalarParam<?> category(BEASTState graph) {
        return assertInstanceOf(IntScalarParam.class, node(graph, "category"));
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
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("categorical-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
