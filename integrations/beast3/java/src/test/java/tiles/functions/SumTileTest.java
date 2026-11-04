package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Loggable;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
import beastconfig.LoggerSelector;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.IntVectorSum;
import tiling.RealVectorSum;

class SumTileTest {
    private static final double TOLERANCE = 1e-10;
    private static final String RANDOM_VECTOR =
            "Vector<PositiveReal> rates ~ IID(base=LogNormal(logMean=0.0,logSd=0.5),num=3)\n";

    private static Stream<Arguments> fixedVectors() {
        return Stream.of(
                Arguments.of("Real", "[1,2,3]", 6.0),
                Arguments.of("Integer", "[1,2,3]", 6.0),
                Arguments.of("Real", "[-2.5,0.0,1.5]", -1.0),
                Arguments.of("Integer", "[-2,0,-1]", -3.0),
                Arguments.of("Real", "[0.0]", 0.0),
                Arguments.of("Integer", "[0]", 0.0),
                Arguments.of("Integer", "range(start=1,end=3)", 6.0),
                Arguments.of("Real", "repeat(value=2.5,num=3)", 7.5),
                Arguments.of("Integer", "repeat(value=2,num=3)", 6.0),
                Arguments.of("Real", "[2147483647,1]", 2147483648.0),
                Arguments.of("Integer", "[2147483647,1,-1]", 2147483647.0),
                Arguments.of("Integer", "[-2147483647,-1]", -2147483648.0));
    }

    @ParameterizedTest
    @MethodSource("fixedVectors")
    void respectsTheDeclaredOverloadAndDoesNotCreateSamplingState(String type, String expression, double expected)
            throws Exception {
        BEASTState graph = compile(type + " total = sum(vector=" + expression + ")");
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.calculationNodes.size());
        var value = graph.calculationNodes.keySet().iterator().next();
        if (type.equals("Integer")) {
            IntVectorSum sum = assertInstanceOf(IntVectorSum.class, value);
            assertSame(Int.INSTANCE, sum.getDomain());
            assertEquals((int) expected, sum.get());
        } else {
            RealVectorSum sum = assertInstanceOf(RealVectorSum.class, value);
            assertSame(Real.INSTANCE, sum.getDomain());
            assertEquals(expected, sum.get(), 0.0);
        }
        LoggerSelector.addMissingLoggerSpecs(graph);
        assertTrue(graph.fileLoggerSpecs.getFirst().parameters().contains(value));
        assertTrue(graph.screenLoggerSpecs.getFirst().parameters().contains(value));
        assertEquals(expected, logged((Loggable) value), 0.0);
    }

    @ParameterizedTest
    @CsvSource({"sum(values), false", "sum(vector=values), false", "sum(vector=values), true"})
    void supportsNamedVectorsPositionalCallsAndImports(String call, boolean imported) throws Exception {
        String source = (imported ? "use phylospec.functions.math\n" : "") + loggedVectorSource(call);
        assertLoggedVectorSum(compile(source));
    }

    @Test
    void supportsQualifiedAstCalls() throws Exception {
        List<Stmt> statements = parse(loggedVectorSource("sum(vector=values)"));
        Stmt.Assignment total = (Stmt.Assignment) statements.get(1);
        ((Expr.Call) total.expression).functionName = "phylospec.functions.math.sum";
        assertLoggedVectorSum(compile(statements));
    }

    private static String loggedVectorSource(String call) {
        return """
                Vector<NonNegativeInteger> values = [0,2,3]
                Integer total = %s
                Integer copy = total
                mcmc {
                    Logger trace = fileLogger(logEvery=1,file="sum.log",parameters=[copy])
                }
                """.formatted(call);
    }

    private static void assertLoggedVectorSum(BEASTState graph) {
        assertEquals(1, graph.calculationNodes.size());
        IntVectorSum sum = assertInstanceOf(
                IntVectorSum.class,
                graph.fileLoggerSpecs.getFirst().parameters().getFirst());
        assertEquals(5, sum.get());
    }

    @Test
    void doesNotCoerceAnIntegerVectorToTheRealOverload() {
        assertThrows(TypeError.class, () -> compile("""
                Vector<Integer> values = [1,2,3]
                Real total = sum(vector=values)
                """));
    }

    @Test
    void integerSumFeedsTheNativeDiscreteUniformLikelihood() throws Exception {
        BEASTState graph = compile("""
                Vector<Integer> values = range(start=1,end=3)
                Integer total = sum(vector=values)
                Integer y ~ DiscreteUniform(lower=0,upper=total) observed as 4
                """);
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
        assertEquals(1, graph.likelihoodDistributions.size());
        IntUniform likelihood = assertInstanceOf(IntUniform.class, graph.likelihoodDistributions.getFirst());
        IntVectorSum sum = assertInstanceOf(IntVectorSum.class, likelihood.upperInput.get());
        assertEquals(6, sum.get());
        assertEquals(-Math.log(7), likelihood.calculateLogP(), TOLERANCE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedSumTracksTheVectorLikelihoodAndRestoredPosterior(boolean acceptFirst) throws Exception {
        BEASTState baseline = compile(RANDOM_VECTOR);
        BEASTState graph = compile(RANDOM_VECTOR + """
                Real total = sum(vector=rates)
                Real copy = total
                Real y1 ~ Normal(mean=total,sd=1.0) observed as 2.0
                Real y2 ~ Normal(mean=copy,sd=2.0) observed as -1.0
                """);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.calculationNodes.size());
        assertEquals(2, graph.likelihoodDistributions.size());
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());
        RealVectorParam<?> rates =
                (RealVectorParam<?>) graph.stateNodes.keySet().iterator().next();
        RealVectorSum sum = assertInstanceOf(
                RealVectorSum.class, graph.calculationNodes.keySet().iterator().next());
        assertSame(rates, sum.vectorInput.get());
        assertTrue(rates.getOutputs().contains(sum));
        for (Distribution distribution : graph.likelihoodDistributions) {
            Normal likelihood = assertInstanceOf(Normal.class, distribution);
            assertSame(sum, likelihood.meanInput.get());
            assertTrue(sum.getOutputs().contains(likelihood));
        }
        CompoundDistribution posterior = posterior(graph);
        State state = state(graph, posterior);
        assertEquals(referencePosterior(rates), state.robustlyCalcPosterior(posterior), TOLERANCE);

        boolean[] decisions = {acceptFirst, !acceptFirst, true};
        for (int step = 0; step < decisions.length; step++) {
            List<Double> previousValues = List.copyOf(rates.getElements());
            double previousPosterior = posterior.getCurrentLogP();
            List<Double> previousLikelihoods = graph.likelihoodDistributions.stream()
                    .map(Distribution::getCurrentLogP)
                    .toList();
            state.store(step + 1);
            rates.set(step, previousValues.get(step) + 0.4 + 0.2 * step);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            double proposed = posterior.calculateLogP();
            assertEquals(referenceSum(rates), sum.get(), 0.0);
            assertEquals(referencePosterior(rates), proposed, TOLERANCE);
            assertNotEquals(previousPosterior, proposed, TOLERANCE);
            for (Distribution factor : graph.likelihoodDistributions) {
                Normal likelihood = (Normal) factor;
                assertEquals(referenceLikelihood(likelihood, sum.get()), likelihood.getCurrentLogP(), TOLERANCE);
            }
            List<Double> proposedLikelihoods = graph.likelihoodDistributions.stream()
                    .map(Distribution::getCurrentLogP)
                    .toList();
            if (decisions[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
                assertEquals(previousValues, rates.getElements());
            }
            state.setEverythingDirty(false);
            // Inspect restored caches before explicitly recalculating the likelihoods.
            assertEquals(decisions[step] ? proposed : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    decisions[step] ? proposedLikelihoods : previousLikelihoods,
                    graph.likelihoodDistributions.stream()
                            .map(Distribution::getCurrentLogP)
                            .toList());
            assertEquals(referenceSum(rates), sum.get(), 0.0);
            assertEquals(sum.get(), logged(sum), 0.0);
            for (Distribution factor : graph.likelihoodDistributions) {
                Normal likelihood = (Normal) factor;
                assertEquals(referenceLikelihood(likelihood, sum.get()), likelihood.calculateLogP(), TOLERANCE);
            }
            assertEquals(referencePosterior(rates), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @Test
    void loggerOnlySumRemainsLiveOutsideThePosteriorDependencies() throws Exception {
        BEASTState graph = compile(RANDOM_VECTOR + """
                Real total = sum(rates)
                mcmc {
                    Logger trace = fileLogger(logEvery=1,file="sum.log",parameters=[total])
                }
                """);
        RealVectorParam<?> rates =
                (RealVectorParam<?>) graph.stateNodes.keySet().iterator().next();
        RealVectorSum sum = assertInstanceOf(
                RealVectorSum.class,
                graph.fileLoggerSpecs.getFirst().parameters().getFirst());
        CompoundDistribution posterior = posterior(graph);
        State state = state(graph, posterior);
        state.robustlyCalcPosterior(posterior);
        double initial = sum.get();
        state.store(1);
        rates.set(0, rates.get(0) + 1.0);
        state.storeCalculationNodes();
        state.checkCalculationNodesDirtiness();
        assertFalse(state.getCurrentCalculationNodes().contains(sum));
        posterior.calculateLogP();
        assertEquals(initial + 1.0, sum.get(), TOLERANCE);
        assertEquals(initial + 1.0, logged(sum), TOLERANCE);
        state.restore();
        state.restoreCalculationNodes();
        state.setEverythingDirty(false);
        assertEquals(initial, sum.get(), 0.0);
        assertEquals(initial, logged(sum), 0.0);
    }

    static BEASTState compile(String source) throws Exception {
        return compile(parse(source));
    }

    private static List<Stmt> parse(String source) {
        var lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        var parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return parser.parse();
    }

    private static BEASTState compile(List<Stmt> statements) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(statements)));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("sum-test"));
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return graph;
    }

    private static CompoundDistribution posterior(BEASTState graph) throws Exception {
        List<Distribution> factors = new ArrayList<>(graph.priorDistributions.values());
        factors.addAll(graph.likelihoodDistributions);
        CompoundDistribution posterior = new CompoundDistribution();
        posterior.initByName("distribution", factors);
        return posterior;
    }

    private static State state(BEASTState graph, Distribution posterior) {
        State state = new State();
        state.initByName("stateNode", new ArrayList<>(graph.stateNodes.keySet()));
        state.initialise();
        state.setPosterior(posterior);
        return state;
    }

    private static double referenceSum(RealVectorParam<?> values) {
        double sum = 0.0;
        for (double value : values.getElements()) sum += value;
        return sum;
    }

    private static double referencePosterior(RealVectorParam<?> rates) {
        double prior = 0.0;
        for (double value : rates.getElements()) {
            double log = Math.log(value);
            prior += -Math.log(value * 0.5 * Math.sqrt(2 * Math.PI)) - 2 * log * log;
        }
        double sum = referenceSum(rates);
        double first = -0.5 * Math.log(2 * Math.PI) - 0.5 * Math.pow(2.0 - sum, 2);
        double second = -Math.log(2.0 * Math.sqrt(2 * Math.PI)) - Math.pow(-1.0 - sum, 2) / 8.0;
        return prior + first + second;
    }

    private static double referenceLikelihood(Normal distribution, double mean) {
        double sd = distribution.sdInput.get().get();
        double residual = distribution.paramInput.get().get() - mean;
        return -Math.log(sd * Math.sqrt(2 * Math.PI)) - residual * residual / (2 * sd * sd);
    }

    private static double logged(Loggable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }
}
