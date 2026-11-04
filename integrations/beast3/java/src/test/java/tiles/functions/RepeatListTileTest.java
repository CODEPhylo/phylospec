package tiles.functions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.core.Loggable;
import beast.base.inference.CalculationNode;
import beast.base.inference.CompoundDistribution;
import beast.base.inference.Distribution;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.distribution.IntUniform;
import beast.base.spec.inference.distribution.Normal;
import beast.base.spec.inference.parameter.BoolScalarParam;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.ExpValue;
import tiling.IntVectorFromScalars;
import tiling.IntVectorSum;
import tiling.RealVectorFromScalars;
import tiling.RealVectorSum;
import tiling.SqrtValue;

class RepeatListTileTest {
    private static final double TOLERANCE = 1e-10;

    private static Stream<Arguments> fixedValues() {
        return Stream.of(
                Arguments.of("Boolean", "true", true),
                Arguments.of("Boolean", "false", false),
                Arguments.of("String", "\"oak\"", "oak"),
                Arguments.of("String", "\"\"", ""));
    }

    @ParameterizedTest
    @MethodSource("fixedValues")
    void repeatsFixedBooleansAndStringsThroughNamedAndInlineCalls(String type, String literal, Object expected)
            throws Exception {
        Built built = compile("""
                %s original = %s
                Vector<%s> values = repeat(value=original,num=3)
                Vector<%s> inline = repeat(value=%s,num=2)
                %s selected = values[2]
                %s inlineSelected = repeat(value=%s,num=1)[1]
                NonNegativeInteger count = num(values)
                NonNegativeInteger inlineCount = num(repeat(value=%s,num=2))
                """.formatted(type, literal, type, type, literal, type, type, literal, literal));
        List<?> values = built.list("values");
        assertEquals(3, values.size());
        assertTrue(values.stream().allMatch(value -> value == built.value("original")));
        assertSame(values.getFirst(), built.value("selected"));
        assertEquals(expected, literalValue(built.value("selected")));
        assertEquals(expected, literalValue(built.value("inlineSelected")));
        assertEquals(
                List.of(expected, expected),
                built.list("inline").stream()
                        .map(RepeatListTileTest::literalValue)
                        .toList());
        assertEquals(TypeToken.listOf(built.types.get("original")), built.types.get("values"));
        assertEquals(3, ((IntScalar<?>) built.value("count")).get());
        assertEquals(2, ((IntScalar<?>) built.value("inlineCount")).get());
        assertNoSamplingState(built.graph);
        assertTrue(built.graph.calculationNodes.isEmpty());
    }

    @Test
    void preservesNestedStringListTypesAndSharedInnerLists() throws Exception {
        Built built = compile("""
                Vector<String> names = ["oak","pine"]
                Vector<Vector<String>> rows = repeat(value=names,num=3)
                Vector<String> selectedRow = rows[2]
                String selectedName = rows[3][2]
                NonNegativeInteger count = num(rows)
                """);
        assertEquals(3, built.list("rows").size());
        assertTrue(built.list("rows").stream().allMatch(row -> row == built.value("names")));
        assertSame(built.value("names"), built.value("selectedRow"));
        assertEquals("pine", built.value("selectedName"));
        assertEquals(new TypeToken<List<List<String>>>() {}, built.types.get("rows"));
        assertEquals(3, ((IntScalar<?>) built.value("count")).get());
        assertNoSamplingState(built.graph);
    }

    @Test
    void supportsImportedAndQualifiedCalls() throws Exception {
        Built imported = compile("""
                use phylospec.functions.math
                Vector<String> values = repeat(value="oak",num=2)
                """);
        assertEquals(List.of("oak", "oak"), imported.list("values"));

        List<Stmt> statements = parse("Vector<String> values = repeat(value=\"pine\",num=2)");
        ((Expr.Call) ((Stmt.Assignment) statements.getFirst()).expression).functionName =
                "phylospec.functions.math.repeat";
        assertEquals(List.of("pine", "pine"), compile(statements).list("values"));
    }

    @Test
    void keepsFixedNumericVectorsOnTheirNativePaths() throws Exception {
        Built built = compile("""
                Vector<Integer> integers = repeat(value=-2,num=3)
                Vector<Real> reals = repeat(value=2.5,num=3)
                Integer integerTotal = sum(integers)
                Real realTotal = sum(reals)
                """);
        IntVectorParam<?> integers = assertInstanceOf(IntVectorParam.class, built.value("integers"));
        RealVectorParam<?> reals = assertInstanceOf(RealVectorParam.class, built.value("reals"));
        assertEquals(List.of(-2, -2, -2), integers.getElements());
        assertEquals(List.of(2.5, 2.5, 2.5), reals.getElements());
        assertSame(integers, ((IntVectorSum) built.value("integerTotal")).vectorInput.get());
        assertSame(reals, ((RealVectorSum) built.value("realTotal")).vectorInput.get());
        assertEquals(-6, number(built.value("integerTotal")), 0.0);
        assertEquals(7.5, number(built.value("realTotal")), 0.0);
        assertNoSamplingState(built.graph);
        assertEquals(2, built.graph.calculationNodes.size());
    }

    @Test
    void keepsTheExistingLiteralSimplexBackendMappingAvailableToTheTiler() throws Exception {
        // Match the backend-only sitemodels/substitutionModels.phylospec regression.
        // This checks selection and application of the existing valid simplex mapping;
        // it does not claim that the core type resolver accepts repeat as a Simplex.
        List<Stmt> statements = new EvaluateLiterals().transform(new RemoveGroupings().transform(parse("""
                QMatrix model = f81(baseFrequencies=repeat(value=0.25,num=4))
                """)));
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        StochasticityResolver stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        List<Tile<?, BEASTState>> selected = evaluator.getBestTiling(statements);
        assertEquals(1, selected.size());
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("repeat-simplex-backend-test"));
        graph.initializeBEASTObjects();
        HKY model = assertInstanceOf(HKY.class, selected.getFirst().apply(graph, new IdentityHashMap<>()));
        SimplexParam frequencies = assertInstanceOf(
                SimplexParam.class,
                model.frequenciesInput.get().frequenciesInput.get());
        assertEquals(List.of(0.25, 0.25, 0.25, 0.25), frequencies.getElements());
        assertNoSamplingState(graph);
        assertTrue(graph.calculationNodes.isEmpty());
    }

    @Test
    void acceptsAFixedComputedIntegerCountAtTheAdapterBoundary() {
        IntVectorSum count = new IntVectorSum();
        count.initByName("vector", new IntVectorParam<>(new int[] {1, 2}, NonNegativeInt.INSTANCE));
        RepeatListTile tile = new RepeatListTile();
        tile.valueInput.setTile(valueTile("oak", TypeToken.of(String.class)));
        tile.numInput.setTile(valueTile(count, TypeToken.of(IntVectorSum.class)));
        BEASTState graph = new BEASTState("repeat-computed-count-test");
        List<Object> values = tile.applyTile(graph, new IdentityHashMap<>());
        assertEquals(List.of("oak", "oak", "oak"), values);
        assertEquals(new TypeToken<List<String>>() {}, tile.getTypeToken());
        assertNoSamplingState(graph);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonpositiveLengthsInBothTheTypeSystemAndAdapter(int count) {
        assertThrows(TypeError.class, () -> compile("Vector<String> values = repeat(value=\"oak\",num=" + count + ")"));
        RepeatListTile tile = new RepeatListTile();
        tile.valueInput.setTile(valueTile("oak", TypeToken.of(String.class)));
        tile.numInput.setTile(valueTile(
                new IntScalarParam<>(count, Int.INSTANCE), TypeToken.parameterized(IntScalarParam.class, Int.class)));
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> tile.applyTile(new BEASTState("repeat-invalid-count-test"), new IdentityHashMap<>()));
        assertTrue(error.getMessage().contains("positive"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"size", "alias"})
    void rejectsSampledAndAliasedLengths(String count) {
        String source = """
                PositiveInteger size ~ Categorical(probabilities=[0.5,0.5])
                PositiveInteger alias = size
                Vector<String> values = repeat(value="oak",num=%s)
                """.formatted(count);
        assertThrows(TileApplicationError.class, () -> compile(source));
        assertStochasticCountRejected(parse(source));
    }

    @Test
    void rejectsStochasticallyDerivedCountsEvenWhenTheirCurrentLengthIsFixed() {
        List<Stmt> statements = parse("""
                Real x ~ Normal(mean=0.0,sd=1.0)
                Vector<Real> original = repeat(value=x,num=3)
                Vector<String> values = repeat(value="oak",num=num(original))
                """);
        assertStochasticCountRejected(statements);
    }

    private static void assertStochasticCountRejected(List<Stmt> statements) {
        StochasticityResolver stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        Expr.Argument num = ((Expr.Call) ((Stmt.Assignment) statements.getLast()).expression).arguments[1];
        assertEquals(Stochasticity.STOCHASTIC, stochasticity.getStochasticity(num));
        var input = new RepeatListTile()
                .getGeneratorTileInputs().stream()
                        .filter(candidate ->
                                candidate.getPhylospecArgumentName().equals("num"))
                        .findFirst()
                        .orElseThrow();
        var error = assertThrows(
                FailedTilingAttempt.RejectedBoundary.class,
                () -> input.getCompatibleInputTiles(num, Map.of(), stochasticity));
        assertTrue(error.getReason().contains("'num'"));
        assertTrue(error.getReason().contains("random variable"));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void sharedNumericRepeatTracksAcceptedAndRejectedStatesAndLoggerOnlyReads(boolean integer, boolean posteriorUsesSum)
            throws Exception {
        String type = integer ? "Integer" : "Real";
        String source =
                integer ? "Integer x ~ DiscreteUniform(lower=1,upper=6)\n" : "Real x ~ Normal(mean=0.0,sd=2.0)\n";
        BEASTState baseline = compile(source).graph;
        String observations = !posteriorUsesSum
                ? ""
                : integer
                        ? "Integer y1 ~ DiscreteUniform(lower=0,upper=total) observed as 1\n"
                                + "Integer y2 ~ DiscreteUniform(lower=0,upper=copy) observed as 2\n"
                        : "Real y1 ~ Normal(mean=total,sd=1.0) observed as 2.0\n"
                                + "Real y2 ~ Normal(mean=copy,sd=2.0) observed as -1.0\n";
        Built built = compile(source + """
                Vector<%s> values = repeat(value=x,num=3)
                %s selected = values[2]
                NonNegativeInteger count = num(values)
                %s total = sum(values)
                %s copy = total
                %s
                mcmc {
                    Logger trace = fileLogger(logEvery=1,file="repeat.log",parameters=[total])
                }
                """.formatted(type, type, type, type, observations));
        BEASTState graph = built.graph;
        assertSameSamplingStructure(baseline, graph);
        assertEquals(1, graph.stateNodes.size());
        assertEquals(1, graph.priorDistributions.size());
        assertEquals(1, graph.calculationNodes.size());
        assertEquals(posteriorUsesSum ? 2 : 0, graph.likelihoodDistributions.size());
        StateNode x = graph.stateNodes.keySet().iterator().next();
        List<?> values = built.list("values");
        assertEquals(3, values.size());
        assertTrue(values.stream().allMatch(value -> value == x));
        assertSame(x, built.value("selected"));
        assertEquals(TypeToken.listOf(graph.stateNodes.get(x)), built.types.get("values"));
        CalculationNode sum = assertInstanceOf(CalculationNode.class, built.value("total"));
        assertSame(sum, built.value("copy"));
        assertSame(sum, graph.fileLoggerSpecs.getFirst().parameters().getFirst());
        List<?> scalarInputs = integer
                ? assertInstanceOf(IntVectorFromScalars.class, ((IntVectorSum) sum).vectorInput.get())
                        .valuesInput
                        .get()
                : assertInstanceOf(RealVectorFromScalars.class, ((RealVectorSum) sum).vectorInput.get())
                        .valuesInput
                        .get();
        assertEquals(3, scalarInputs.size());
        assertTrue(scalarInputs.stream().allMatch(value -> value == x));
        for (Distribution factor : graph.likelihoodDistributions) {
            assertSame(sum, integer ? ((IntUniform) factor).upperInput.get() : ((Normal) factor).meanInput.get());
        }

        CompoundDistribution posterior = posterior(graph);
        State state = state(graph, posterior);
        assertEquals(
                referencePosterior(integer, posteriorUsesSum, number(x)),
                state.robustlyCalcPosterior(posterior),
                TOLERANCE);
        // Prime the sum before proposals; logger-only reads must not retain this value.
        assertEquals(3 * number(x), number(sum), TOLERANCE);
        double[] proposals = integer ? new double[] {2, 4, 3} : new double[] {0.4, 1.2, -0.5};
        boolean[] accepted = {true, false, true};
        for (int step = 0; step < accepted.length; step++) {
            double previousX = number(x);
            double previousPosterior = posterior.getCurrentLogP();
            List<Double> previousLikelihoods = graph.likelihoodDistributions.stream()
                    .map(Distribution::getCurrentLogP)
                    .toList();
            state.store(step + 1);
            if (integer) ((IntScalarParam<?>) x).set((int) proposals[step]);
            else ((RealScalarParam<?>) x).set(proposals[step]);
            state.storeCalculationNodes();
            state.checkCalculationNodesDirtiness();
            assertEquals(posteriorUsesSum, state.getCurrentCalculationNodes().contains(sum));
            double proposedPosterior = posterior.calculateLogP();
            assertEquals(referencePosterior(integer, posteriorUsesSum, proposals[step]), proposedPosterior, TOLERANCE);
            assertEquals(3 * proposals[step], number(sum), TOLERANCE);
            assertEquals(3 * proposals[step], logged((Loggable) sum), TOLERANCE);
            List<Double> proposedLikelihoods = graph.likelihoodDistributions.stream()
                    .map(Distribution::getCurrentLogP)
                    .toList();
            if (accepted[step]) {
                state.acceptCalculationNodes();
            } else {
                state.restore();
                state.restoreCalculationNodes();
            }
            state.setEverythingDirty(false);
            // Check cached factors before any explicit recalculation after rejection.
            assertEquals(accepted[step] ? proposedPosterior : previousPosterior, posterior.getCurrentLogP(), TOLERANCE);
            assertEquals(
                    accepted[step] ? proposedLikelihoods : previousLikelihoods,
                    graph.likelihoodDistributions.stream()
                            .map(Distribution::getCurrentLogP)
                            .toList());
            assertEquals(accepted[step] ? proposals[step] : previousX, number(x), 0.0);
            assertEquals(3 * number(x), number(sum), TOLERANCE);
            assertEquals(number(sum), logged((Loggable) sum), 0.0);
            assertEquals(3, ((IntScalar<?>) built.value("count")).get());
            assertTrue(values.stream().allMatch(value -> value == x));
            assertEquals(
                    referencePosterior(integer, posteriorUsesSum, number(x)), posterior.calculateLogP(), TOLERANCE);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"exp", "sqrt"})
    void retainsConcreteDerivedScalarTypesAndReferences(String function) throws Exception {
        boolean exponential = function.equals("exp");
        String source = exponential
                ? "Real x ~ Normal(mean=0.0,sd=1.0)\n"
                : "PositiveReal x ~ LogNormal(logMean=0.0,logSd=0.5)\n";
        String type = exponential ? "PositiveReal" : "NonNegativeReal";
        Built built = compile(source + """
                %s derived = %s(x)
                Vector<%s> values = repeat(value=derived,num=3)
                %s selected = values[2]
                Real total = sum(values)
                Real inlineTotal = sum(repeat(value=%s(x),num=2))
                """.formatted(type, function, type, type, function));
        assertSameSamplingStructure(compile(source).graph, built.graph);
        RealScalarParam<?> x =
                (RealScalarParam<?>) built.graph.stateNodes.keySet().iterator().next();
        Object derived = built.value("derived");
        assertSame(
                x,
                exponential
                        ? assertInstanceOf(ExpValue.class, derived).xInput.get()
                        : assertInstanceOf(SqrtValue.class, derived).xInput.get());
        assertTrue(built.list("values").stream().allMatch(value -> value == derived));
        assertSame(derived, built.value("selected"));
        assertEquals(TypeToken.listOf(TypeToken.of(derived.getClass())), built.types.get("values"));
        RealVectorSum total = assertInstanceOf(RealVectorSum.class, built.value("total"));
        RealVectorFromScalars vector = assertInstanceOf(RealVectorFromScalars.class, total.vectorInput.get());
        assertTrue(vector.valuesInput.get().stream().allMatch(value -> value == derived));
        RealVectorSum inline = assertInstanceOf(RealVectorSum.class, built.value("inlineTotal"));
        List<?> inlineValues = assertInstanceOf(RealVectorFromScalars.class, inline.vectorInput.get())
                .valuesInput
                .get();
        assertEquals(2, inlineValues.size());
        assertSame(inlineValues.getFirst(), inlineValues.getLast());
        assertNotSame(derived, inlineValues.getFirst());
        assertEquals(4, built.graph.calculationNodes.size());
        for (double value : new double[] {0.5, 2.0, 1.0}) {
            x.set(value);
            double expected = exponential ? Math.exp(value) : Math.sqrt(value);
            assertEquals(3 * expected, total.get(), TOLERANCE);
            assertEquals(2 * expected, inline.get(), TOLERANCE);
            assertEquals(expected, number(built.value("selected")), 0.0);
            assertEquals(3 * expected, logged(total), TOLERANCE);
        }
    }

    private static Object literalValue(Object value) {
        return value instanceof BoolScalarParam bool ? bool.get() : value;
    }

    private static double number(Object value) {
        return value instanceof IntScalar<?> integer ? integer.get() : ((RealScalar<?>) value).get();
    }

    private static double referencePosterior(boolean integer, boolean withLikelihoods, double x) {
        double prior = integer ? -Math.log(6) : normalLogDensity(x, 0.0, 2.0);
        if (!withLikelihoods) return prior;
        return prior
                + (integer
                        ? -2 * Math.log(3 * x + 1)
                        : normalLogDensity(2.0, 3 * x, 1.0) + normalLogDensity(-1.0, 3 * x, 2.0));
    }

    private static double normalLogDensity(double value, double mean, double sd) {
        double residual = value - mean;
        return -Math.log(sd * Math.sqrt(2 * Math.PI)) - residual * residual / (2 * sd * sd);
    }

    private static void assertNoSamplingState(BEASTState graph) {
        assertTrue(graph.stateNodes.isEmpty());
        assertTrue(graph.priorDistributions.isEmpty());
        assertTrue(graph.likelihoodDistributions.isEmpty());
        assertTrue(graph.operators.isEmpty());
    }

    private static void assertSameSamplingStructure(BEASTState baseline, BEASTState graph) {
        assertEquals(baseline.stateNodes.size(), graph.stateNodes.size());
        assertEquals(baseline.priorDistributions.size(), graph.priorDistributions.size());
        assertEquals(
                baseline.operators.stream().map(Object::getClass).toList(),
                graph.operators.stream().map(Object::getClass).toList());
    }

    private static CompoundDistribution posterior(BEASTState graph) {
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

    private static double logged(Loggable value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            value.log(0, out);
        }
        return Double.parseDouble(bytes.toString(StandardCharsets.UTF_8).trim());
    }

    private static Built compile(String source) throws Exception {
        return compile(parse(source));
    }

    private static List<Stmt> parse(String source) {
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return parser.parse();
    }

    private static Built compile(List<Stmt> statements) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(statements)));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        StochasticityResolver stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        EvaluateTiles<BEASTState> evaluator =
                new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        List<Tile<?, BEASTState>> tiles = evaluator.getBestTiling(statements);
        BEASTState graph = evaluator.applyBestTiling(new BEASTState("repeat-list-test"));
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, TypeToken<?>> types = new LinkedHashMap<>();
        Set<Tile<?, ?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Tile<?, BEASTState> tile : tiles) {
            collectAssignments(tile, graph, values, types, visited);
        }
        catalog.configureState(graph);
        graph.initializeBEASTObjects();
        return new Built(graph, values, types);
    }

    @SuppressWarnings("unchecked")
    private static void collectAssignments(
            Tile<?, BEASTState> tile,
            BEASTState graph,
            Map<String, Object> values,
            Map<String, TypeToken<?>> types,
            Set<Tile<?, ?>> visited) {
        if (!visited.add(tile)) return;
        if (tile.getRootNode() instanceof Stmt.Assignment assignment) {
            // Applying an already selected tile returns its memoized object. Referenced
            // assignments may be consumed into another tile and absent from the top-level list.
            values.put(assignment.name, tile.apply(graph, new IdentityHashMap<>()));
            types.put(assignment.name, tile.getTypeToken());
        }
        try {
            for (Tile<?, ?> child : tile.getWiredUpInputs().values()) {
                collectAssignments((Tile<?, BEASTState>) child, graph, values, types, visited);
            }
        } catch (InconsistentTilingException exception) {
            fail("The selected tiling contains inconsistent shared inputs.", exception);
        }
    }

    private record Built(BEASTState graph, Map<String, Object> values, Map<String, TypeToken<?>> types) {
        Object value(String name) {
            assertTrue(values.containsKey(name), "No compiled assignment named " + name);
            return values.get(name);
        }

        List<?> list(String name) {
            return assertInstanceOf(List.class, value(name));
        }
    }

    private static <T> Tile<T, BEASTState> valueTile(T value, TypeToken<?> type) {
        Tile<T, BEASTState> tile = new Tile<>() {
            @Override
            public TypeToken<?> getTypeToken() {
                return type;
            }

            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
    }
}
