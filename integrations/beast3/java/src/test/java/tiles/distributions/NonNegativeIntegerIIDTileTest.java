package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.distribution.IID;
import beast.base.spec.inference.distribution.Poisson;
import beast.base.spec.inference.operator.IntRandomWalkOperator;
import beast.base.spec.inference.operator.SwapOperator;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import beastconfig.BEASTState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.lexer.Lexer;
import org.phylospec.parser.Parser;
import org.phylospec.tiling.EvaluateTiles;
import org.phylospec.tiling.TileCatalog;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiling.BoundDistribution;
import tiling.IntVectorSum;

class NonNegativeIntegerIIDTileTest {
    private static final double TOLERANCE = 1e-10;

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 6})
    void drawsOnlyOneZeroInitializedVectorAndUsesNativeOperators(int size) throws Exception {
        BEASTState graph = evaluate("""
                Vector<NonNegativeInteger> counts ~ IID(base=Poisson(rate=2.3),num=%d)
                Integer total = sum(counts)
                NonNegativeInteger n = num(counts)
                mcmc {
                    Logger totals = fileLogger(logEvery=10,file="sum.log",parameters=[total])
                    Logger lengths = fileLogger(logEvery=10,file="num.log",parameters=[n])
                }
                """.formatted(size));
        IntVectorParam<?> counts = counts(graph);
        IID<?, ?, ?> iid = assertInstanceOf(IID.class, graph.priorDistributions.get(counts));
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertEquals(size, counts.size());
        assertSame(NonNegativeInt.INSTANCE, counts.getDomain());
        assertEquals(0, counts.getLower());
        assertEquals(Integer.MAX_VALUE, counts.getUpper());
        // Registration order is not part of the logger contract.
        var summaries = graph.fileLoggerSpecs.stream()
                .flatMap(spec -> spec.parameters().stream())
                .toList();
        assertEquals(2, summaries.size());
        IntVectorSum total = summaries.stream()
                .filter(IntVectorSum.class::isInstance)
                .map(IntVectorSum.class::cast)
                .findFirst()
                .orElseThrow();
        IntScalarParam<?> n = summaries.stream()
                .filter(IntScalarParam.class::isInstance)
                .map(IntScalarParam.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(0, total.get());
        assertEquals(size, n.get());
        assertTrue(counts.getElements().stream().allMatch(value -> value == 0));
        assertSame(counts, iid.paramInput.get());
        assertInstanceOf(Poisson.class, iid.distInput.get());
        assertNull(iid.distInput.get().paramInput.get());
        assertEquals(-2.3 * size, iid.calculateLogP(), TOLERANCE);
        assertEquals(size == 1 ? 1 : 2, graph.operators.size());
        assertTrue(graph.operators.stream()
                .allMatch(operator -> operator.listStateNodes().equals(List.of(counts))));
        IntRandomWalkOperator walk = assertInstanceOf(IntRandomWalkOperator.class, graph.operators.getFirst());
        assertEquals(IntRandomWalkOperator.class, walk.getClass());
        assertSame(counts, walk.parameterInput.get());
        assertEquals(1, walk.windowSizeInput.get());
        assertEquals(10.0, walk.m_pWeight.get());

        nativeState(graph, posterior(graph));
        Randomizer.setSeed(2718);
        boolean rejectedAtZero = false;
        for (int attempt = 0; attempt < 100; attempt++) {
            for (int i = 0; i < size; i++) counts.set(i, 0);
            double hastings = assertDoesNotThrow(walk::proposal);
            rejectedAtZero |= hastings == Double.NEGATIVE_INFINITY;
            assertTrue(counts.getElements().stream().allMatch(value -> value >= 0));
        }
        assertTrue(rejectedAtZero, "Native proposals at zero must reject invalid moves without throwing.");
        for (int i = 0; i < size; i++) counts.set(i, 10 * (i + 1));
        List<Integer> beforeWalk = counts.getElements();
        for (int attempt = 0; attempt < 100 && counts.getElements().equals(beforeWalk); attempt++) walk.proposal();
        assertNotEquals(beforeWalk, counts.getElements(), "The native integer random walk must move the vector.");
        assertEquals(
                1,
                IntStream.range(0, size)
                        .map(i -> Math.abs(counts.get(i) - beforeWalk.get(i)))
                        .sum());
        if (size > 1) {
            SwapOperator swap = assertInstanceOf(SwapOperator.class, graph.operators.getLast());
            assertEquals(SwapOperator.class, swap.getClass());
            assertSame(counts, swap.intparameterInput.get());
            assertEquals(10.0, swap.m_pWeight.get());
            List<Integer> beforeSwap = counts.getElements();
            assertEquals(0.0, swap.proposal());
            assertNotEquals(beforeSwap, counts.getElements());
            assertEquals(
                    beforeSwap.stream().sorted().toList(),
                    counts.getElements().stream().sorted().toList());
        }
        assertEquals(poissonLogP(iid), iid.calculateLogP(), TOLERANCE);
        assertEquals(counts.getElements().stream().mapToInt(Integer::intValue).sum(), total.get());
        assertEquals(size, n.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedNamedRateAndVectorChangesSurviveAcceptanceAndRejection(boolean changeRate) throws Exception {
        BEASTState graph = evaluate("""
                PositiveReal rate ~ Gamma(shape=2.0,rate=1.0)
                Vector<NonNegativeInteger> counts ~ IID(base=Poisson(rate=rate),num=3)
                Vector<NonNegativeInteger> observations ~ IID(base=Poisson(rate=rate),num=3) observed as [0,1,2]
                """);
        IntVectorParam<?> counts = counts(graph);
        RealScalarParam<?> rate = assertInstanceOf(
                RealScalarParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> "rate".equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
        IID<?, ?, ?> prior = assertInstanceOf(IID.class, graph.priorDistributions.get(counts));
        IID<?, ?, ?> likelihood = assertInstanceOf(IID.class, graph.likelihoodDistributions.getFirst());
        IntVectorParam<?> observed = assertInstanceOf(IntVectorParam.class, likelihood.paramInput.get());
        assertEquals(List.of(0, 1, 2), observed.getElements());
        assertFalse(graph.stateNodes.containsKey(observed));
        assertEquals(2, graph.stateNodes.size());
        assertEquals(2, graph.priorDistributions.size());
        assertEquals(1, graph.likelihoodDistributions.size());
        assertEquals(3, graph.operators.size());
        assertSame(PositiveReal.INSTANCE, rate.getDomain());
        for (var iid : List.of(prior, likelihood)) {
            Poisson law = assertInstanceOf(Poisson.class, iid.distInput.get());
            assertSame(rate, law.lambdaInput.get());
            assertNull(law.paramInput.get());
            assertTrue(rate.getOutputs().contains(law));
            assertTrue(law.getOutputs().contains(iid));
        }
        CompoundDistribution posterior = posterior(graph);
        State state = nativeState(graph, posterior);
        assertEquals(referencePosterior(prior, likelihood, rate), state.robustlyCalcPosterior(posterior), TOLERANCE);
        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            boolean accept = decisions[step];
            List<Integer> previousCounts = counts.getElements();
            double previousRate = rate.get();
            double previousPrior = prior.getCurrentLogP();
            double previousLikelihood = likelihood.getCurrentLogP();
            double previousPosterior = posterior.getCurrentLogP();
            state.store(step + 1);
            if (changeRate) rate.set(previousRate + 0.5 * (step + 1));
            else counts.set(0, previousCounts.getFirst() + step + 2);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposedPosterior = posterior.calculateLogP();
            double expectedPrior = poissonLogP(prior);
            double expectedLikelihood = poissonLogP(likelihood);
            assertNotEquals(previousPrior, expectedPrior, TOLERANCE);
            assertEquals(expectedPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(expectedLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(referencePosterior(prior, likelihood, rate), proposedPosterior, TOLERANCE);
            assertEquals(List.of(0, 1, 2), observed.getElements());
            if (changeRate) assertEquals(previousCounts, counts.getElements());
            else assertEquals(previousRate, rate.get());
            if (accept) state.acceptCalculationNodes();
            else {
                state.restore();
                state.restoreCalculationNodes();
                assertEquals(previousCounts, counts.getElements());
                assertEquals(previousRate, rate.get());
            }
            state.setEverythingDirty(false);
            // Inspect caches before recalculation can mask a failed restoration.
            assertEquals(accept ? expectedPrior : previousPrior, prior.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? expectedLikelihood : previousLikelihood, likelihood.getCurrentLogP(), TOLERANCE);
            assertEquals(accept ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(referencePosterior(prior, likelihood, rate), posterior.calculateLogP(), TOLERANCE);
            assertEquals(3, counts.size());
        }
    }

    @Test
    void observedVectorCreatesOnlyOneLikelihood() throws Exception {
        BEASTState graph = evaluate("""
                Vector<NonNegativeInteger> counts ~ IID(base=Poisson(rate=2.3),num=3) observed as [0,1,2]
                """);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        IID<?, ?, ?> iid = assertInstanceOf(IID.class, graph.likelihoodDistributions.getFirst());
        assertEquals(
                List.of(0, 1, 2),
                assertInstanceOf(IntVectorParam.class, iid.paramInput.get()).getElements());
        assertNull(iid.distInput.get().paramInput.get());
        assertEquals(poissonLogP(iid), iid.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[0,1]", "[0,1,2,3]"})
    void rejectsWrongObservationLengthsThroughTheTypedPipeline(String observation) {
        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> evaluate("Vector<NonNegativeInteger> counts ~ IID(base=Poisson(rate=2.3),num=3) observed as "
                        + observation));
        assertTrue(error instanceof TypeError || error instanceof TileApplicationError);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4})
    void bindingRejectsWrongObservationLengthsBeforeRegistration(int length) {
        BEASTState graph = new BEASTState("iid-length");
        Poisson law = new Poisson();
        graph.setInput(law, law.lambdaInput, new RealScalarParam<>(2.3, NonNegativeReal.INSTANCE));
        BoundDistribution<IntScalarParam<NonNegativeInt>, Poisson> base = new BoundDistribution<>(
                law, new IntScalarParam<>(0, NonNegativeInt.INSTANCE), ignored -> {}, (parameter, state) -> List.of());
        NonNegativeIntegerIIDTile tile = new NonNegativeIntegerIIDTile();
        tile.baseInput.setTile(valueTile(base));
        tile.numInput.setTile(valueTile(new IntScalarParam<>(3, PositiveInt.INSTANCE)));
        var distribution = tile.applyTile(graph, new IdentityHashMap<>());
        var observation = new IntVectorParam<>(new int[length], NonNegativeInt.INSTANCE);
        TileApplicationError error =
                assertThrows(TileApplicationError.class, () -> distribution.observeAs(graph, observation, "observed"));
        assertTrue(error.getMessage().contains("num"));
        assertNull(distribution.getDistribution().paramInput.get());
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertThrows(RuntimeException.class, base::consumeAsBase, "The scalar base must have been consumed once.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "true"})
    void rejectsInvalidFixedDimensions(String size) {
        assertThrows(
                TypeError.class,
                () -> evaluate("Vector<NonNegativeInteger> counts ~ IID(base=Poisson(rate=2.3),num=" + size + ")"));
    }

    @Test
    void rejectsRandomDimensionAtTheTileBoundary() {
        var statements = new Parser(
                        new Lexer("Integer n ~ DiscreteUniform(lower=1,upper=4)\nInteger size = n").scanTokens())
                .parse();
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var expression = ((Stmt.Assignment) statements.getLast()).expression;
        var error = assertThrows(FailedTilingAttempt.RejectedBoundary.class, () -> new NonNegativeIntegerIIDTile()
                .numInput.getCompatibleInputTiles(expression, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("num"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void doesNotNarrowASignedIntegerBase() {
        assertThrows(
                TileApplicationError.class,
                () -> evaluate("Vector<Integer> counts ~ IID(base=DiscreteUniform(lower=-2,upper=2),num=3)"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void shortChainMovesIntegerCountsAndKeepsFiniteProbabilities(int size) throws Exception {
        String source = """
                Vector<NonNegativeInteger> counts ~ IID(base=Poisson(rate=2.3),num=%d)
                mcmc {
                    Integer chainLength=200
                    Integer defaultLogEvery=10
                    Integer randomSeed=12345
                }
                """.formatted(size);
        Path prefix = directory.resolve("integer-iid-" + size);
        new PhyloSpecRunner(source, List.of("beast2")).runPhyloSpec(prefix.toString());
        var lines = Files.readAllLines(Path.of(prefix + ".log")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        List<String> headers = Arrays.asList(lines.getFirst().trim().split("\\s+"));
        List<Integer> countColumns = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).equals("counts") || headers.get(i).startsWith("counts.")) countColumns.add(i);
        }
        assertEquals(size, countColumns.size());
        var rows = lines.subList(1, lines.size()).stream()
                .map(line -> line.trim().split("\\s+"))
                .toList();
        assertEquals(21, rows.size());
        assertEquals("200", rows.getLast()[0]);
        assertTrue(rows.stream()
                        .map(row -> countColumns.stream().map(i -> row[i]).toList())
                        .distinct()
                        .count()
                > 1);
        for (var row : rows) {
            for (int i = 1; i < row.length; i++) assertTrue(Double.isFinite(Double.parseDouble(row[i])));
            for (int column : countColumns) {
                double count = Double.parseDouble(row[column]);
                assertTrue(count >= 0);
                assertEquals(Math.rint(count), count);
            }
        }
    }

    private static double poissonLogP(IID<?, ?, ?> iid) {
        Poisson law = assertInstanceOf(Poisson.class, iid.distInput.get());
        IntVectorParam<?> values = assertInstanceOf(IntVectorParam.class, iid.paramInput.get());
        double rate = law.lambdaInput.get().get();
        double result = 0.0;
        for (int count : values.getElements()) {
            result += count * Math.log(rate) - rate;
            for (int factor = 2; factor <= count; factor++) result -= Math.log(factor);
        }
        return result;
    }

    private static double referencePosterior(IID<?, ?, ?> prior, IID<?, ?, ?> likelihood, RealScalarParam<?> rate) {
        return poissonLogP(prior) + poissonLogP(likelihood) + Math.log(rate.get()) - rate.get();
    }

    private static CompoundDistribution posterior(BEASTState graph) {
        var distributions = new ArrayList<>(graph.priorDistributions.values());
        distributions.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", distributions);
        return posterior;
    }

    private static State nativeState(BEASTState graph, CompoundDistribution posterior) {
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static <T> Tile<T, BEASTState> valueTile(T value) {
        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {
            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
    }

    private static IntVectorParam<?> counts(BEASTState graph) {
        return assertInstanceOf(
                IntVectorParam.class,
                graph.stateNodes.keySet().stream()
                        .filter(node -> "counts".equals(node.getID()))
                        .findFirst()
                        .orElseThrow());
    }

    private BEASTState evaluate(String source) throws Exception {
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
        BEASTState graph = evaluator.applyBestTiling(
                new BEASTState(directory.resolve("test").toString(), directory));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
