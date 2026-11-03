package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.inference.CompoundDistribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beastconfig.BEASTState;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;
import tiles.distributions.PositiveIIDTile;

class NumTileTest {
    private static final String IID =
            "Vector<PositiveReal> values ~ IID(base=LogNormal(logMean=0.0,logSd=0.5),num=3)\n";
    private static final String LOG_COUNT = """
            NonNegativeInteger n = num(vector=values)
            mcmc {
                Logger trace = fileLogger(logEvery=1,file="num.log",parameters=[n])
            }
            """;

    private static Stream<Arguments> fixedInputs() {
        return Stream.of(
                Arguments.of("Vector<Real> values = repeat(value=2.5,num=3)\n", 3),
                Arguments.of("Vector<Integer> values = range(start=-1,end=1)\n", 3),
                Arguments.of("Vector<String> values = [\"first\",\"second\"]\n", 2));
    }

    @ParameterizedTest
    @MethodSource("fixedInputs")
    void readsFixedNativeVectorsAndListsWithoutSamplingState(String source, int expected) throws Exception {
        BEASTState graph = compile(source + LOG_COUNT);
        assertUnchangedSamplingGraph(compile(source), graph);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertCount(graph, count(graph), expected);
    }

    private static Stream<Arguments> randomInputs() {
        return Stream.of(
                Arguments.of(IID, 3, RealVectorParam.class, 1),
                Arguments.of(
                        "Simplex values ~ Dirichlet(concentration=repeat(value=2.0,num=4))\n",
                        4,
                        SimplexParam.class,
                        1),
                Arguments.of(
                        "PositiveReal values[i] ~ LogNormal(logMean=0.0,logSd=0.5) for i in 1:3\n",
                        3,
                        RealScalarParam.class,
                        3),
                Arguments.of("""
                        PositiveReal a ~ LogNormal(logMean=0.0,logSd=0.5)
                        PositiveReal b ~ LogNormal(logMean=0.0,logSd=0.5)
                        Vector<PositiveReal> values = [a,b,a]
                        """, 3, RealScalarParam.class, 2));
    }

    @ParameterizedTest
    @MethodSource("randomInputs")
    void fixedLengthSurvivesElementChangesAcceptanceAndRestoration(
            String source, int expected, Class<?> parameterClass, int stateCount) throws Exception {
        BEASTState graph = compile(source + LOG_COUNT);
        assertUnchangedSamplingGraph(compile(source), graph);
        assertEquals(stateCount, graph.stateNodes.size());
        assertEquals(stateCount, graph.priorDistributions.size());
        assertTrue(graph.stateNodes.keySet().stream().allMatch(parameterClass::isInstance));
        IntScalarParam<NonNegativeInt> n = count(graph);
        assertCount(graph, n, expected);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", new ArrayList<>(graph.priorDistributions.values()));
        State nativeState = new State();
        nativeState.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        nativeState.initialise();
        nativeState.setPosterior(posterior);
        assertTrue(Double.isFinite(nativeState.robustlyCalcPosterior(posterior)));
        StateNode input = graph.stateNodes.keySet().iterator().next();

        boolean[] decisions = {true, false, true};
        for (int step = 0; step < decisions.length; step++) {
            List<Double> previous = values(input);
            double previousPosterior = posterior.getCurrentLogP();
            nativeState.store(step + 1);
            move(input);
            List<Double> proposed = values(input);
            assertNotEquals(previous, proposed);
            nativeState.storeCalculationNodes();
            nativeState.checkCalculationNodesDirtiness();
            double proposedPosterior = posterior.calculateLogP();
            assertTrue(Double.isFinite(proposedPosterior));
            assertCount(graph, n, expected);
            if (decisions[step]) {
                nativeState.acceptCalculationNodes();
            } else {
                nativeState.restore();
                nativeState.restoreCalculationNodes();
            }
            nativeState.setEverythingDirty(false);
            assertEquals(decisions[step] ? proposed : previous, values(input));
            assertEquals(decisions[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), 1e-10);
            assertCount(graph, n, expected);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"num(values)", "alias", "randomSize"})
    void keepsNumOfRandomVectorStochasticAndRejectsStochasticIIDDimensions(String dimension) {
        List<Stmt> statements = parse(IID + """
                NonNegativeInteger alias = num(values)
                Integer randomSize ~ DiscreteUniform(lower=1,upper=4)
                Vector<PositiveReal> output ~ IID(base=LogNormal(logMean=0.0,logSd=0.5),num=%s)
                """.formatted(dimension));
        StochasticityResolver stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        Expr length = ((Stmt.Assignment) statements.get(1)).expression;
        assertEquals(Stochasticity.STOCHASTIC, stochasticity.getStochasticity(length));
        Expr.Call iid = (Expr.Call) ((Stmt.Draw) statements.getLast()).expression;
        Expr.Argument num = iid.arguments[1];
        assertEquals(Stochasticity.STOCHASTIC, stochasticity.getStochasticity(num));
        var numInput = new PositiveIIDTile()
                .getGeneratorTileInputs().stream()
                        .filter(input -> input.getPhylospecArgumentName().equals("num"))
                        .findFirst()
                        .orElseThrow();
        var error = assertThrows(
                FailedTilingAttempt.RejectedBoundary.class,
                () -> numInput.getCompatibleInputTiles(num, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("'num'"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @Test
    void formerRandomVectorFixturePassesTheTypedRunnerCheck() throws Exception {
        assertNotNull(
                new PhyloSpecRunner(Path.of("src/test/java/tiling/functions/numOfRV.phylospec"), List.of("beast2"))
                        .check());
    }

    private static void assertUnchangedSamplingGraph(BEASTState baseline, BEASTState graph) {
        assertEquals(baseline.stateNodes.size(), graph.stateNodes.size());
        assertEquals(baseline.priorDistributions.size(), graph.priorDistributions.size());
        assertEquals(baseline.likelihoodDistributions.size(), graph.likelihoodDistributions.size());
        assertEquals(baseline.calculationNodes.size(), graph.calculationNodes.size());
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());
    }

    @SuppressWarnings("unchecked")
    private static IntScalarParam<NonNegativeInt> count(BEASTState graph) {
        assertEquals(1, graph.fileLoggerSpecs.size());
        var parameters = graph.fileLoggerSpecs.getFirst().parameters();
        assertEquals(1, parameters.size());
        return (IntScalarParam<NonNegativeInt>) assertInstanceOf(IntScalarParam.class, parameters.getFirst());
    }

    private static void assertCount(BEASTState graph, IntScalarParam<NonNegativeInt> n, int expected) {
        assertSame(NonNegativeInt.INSTANCE, n.getDomain());
        assertEquals(expected, n.get());
        assertFalse(graph.stateNodes.containsKey(n));
        assertFalse(graph.priorDistributions.containsKey(n));
        assertTrue(graph.operators.stream()
                .noneMatch(operator -> operator.listStateNodes().contains(n)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            n.log(0, out);
        }
        assertEquals(
                Integer.toString(expected),
                bytes.toString(StandardCharsets.UTF_8).trim());
    }

    private static List<Double> values(StateNode input) {
        if (input instanceof RealScalarParam<?> scalar) return List.of(scalar.get());
        return List.copyOf(((RealVectorParam<?>) input).getElements());
    }

    private static void move(StateNode input) {
        if (input instanceof SimplexParam simplex) {
            simplex.set(0, simplex.get(0) + 0.02);
            simplex.set(1, simplex.get(1) - 0.02);
        } else if (input instanceof RealVectorParam<?> vector) {
            vector.set(0, vector.get(0) + 0.4);
        } else {
            RealScalarParam<?> scalar = (RealScalarParam<?>) input;
            scalar.set(scalar.get() + 0.4);
        }
    }

    private static List<Stmt> parse(String source) {
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
    }

    private static BEASTState compile(String source) throws Exception {
        var statements = parse(source);
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("num-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }
}
