package tiles.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import beast.base.spec.domain.NonNegativeReal;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beastconfig.BEASTState;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class LinSpaceTileTest {

    @Test
    void preservesOrdinaryAscendingDescendingAndEqualIntervals() {
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0, 5.0), linspace(1.0, 5.0, 5).getElements());
        assertEquals(List.of(5.0, 4.0, 3.0, 2.0, 1.0), linspace(5.0, 1.0, 5).getElements());
        assertEquals(List.of(2.0, 2.0, 2.0, 2.0), linspace(2.0, 2.0, 4).getElements());
        assertEquals(List.of(-2.0, -1.0, 0.0, 1.0), linspace(-2.0, 1.0, 4).getElements());
    }

    @Test
    void singlePointKeepsTheExistingStartValueConvention() {
        assertEquals(List.of(1.0), linspace(1.0, 5.0, 1).getElements());
        assertEquals(List.of(5.0), linspace(5.0, 1.0, 1).getElements());
        assertEquals(
                List.of(-Double.MAX_VALUE),
                linspace(-Double.MAX_VALUE, Double.MAX_VALUE, 1).getElements());
    }

    @Test
    void preservesExactEndpointsDespiteRoundedStepSizes() {
        RealVectorParam<Real> result = linspace(1.0, 0.0, 50);
        assertEquals(1.0, result.get(0), 0.0);
        assertEquals(0.0, result.get(49), 0.0);
        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i) <= result.get(i - 1));
        }
    }

    @ParameterizedTest
    @CsvSource({"3,false", "3,true", "5,false", "5,true"})
    void oppositeFiniteExtremeEndpointsProduceFiniteOrderedValues(int num, boolean descending) {
        double start = descending ? Double.MAX_VALUE : -Double.MAX_VALUE;
        double end = -start;
        RealVectorParam<Real> result = linspace(start, end, num);
        assertEquals(start, result.get(0), 0.0);
        assertEquals(end, result.get(num - 1), 0.0);
        assertEquals(0.0, result.get(num / 2), 0.0);
        for (int i = 0; i < num; i++) {
            double value = result.get(i);
            assertTrue(Double.isFinite(value));
            assertTrue(value >= -Double.MAX_VALUE && value <= Double.MAX_VALUE);
            if (i > 0) {
                assertTrue(descending ? value <= result.get(i - 1) : value >= result.get(i - 1));
            }
        }
        if (num == 5) {
            // Quarter points are +/- MAX_VALUE / 2; allow normal last-bit rounding.
            double expectedFirstQuarter = start / 2.0;
            double expectedThirdQuarter = end / 2.0;
            assertEquals(expectedFirstQuarter, result.get(1), 2.0 * Math.ulp(expectedFirstQuarter));
            assertEquals(expectedThirdQuarter, result.get(3), 2.0 * Math.ulp(expectedThirdQuarter));
        }
    }

    @Test
    void retainsRepresentableInteriorValuesWhenTheStepUnderflows() {
        double minimum = Double.MIN_VALUE;
        assertEquals(
                List.of(0.0, 0.0, minimum, 2.0 * minimum, 2.0 * minimum),
                linspace(0.0, 2.0 * minimum, 5).getElements());
        assertEquals(
                List.of(2.0 * minimum, 2.0 * minimum, minimum, 0.0, 0.0),
                linspace(2.0 * minimum, 0.0, 5).getElements());
    }

    @Test
    void preservesNarrowerEndpointDomainsAndReturnsARealVector() {
        RealScalarParam<PositiveReal> start = new RealScalarParam<>(1.0, PositiveReal.INSTANCE);
        RealScalarParam<NonNegativeReal> end = new RealScalarParam<>(0.0, NonNegativeReal.INSTANCE);
        RealVectorParam<Real> result = linspace(start, end, 3);
        assertEquals(List.of(1.0, 0.5, 0.0), result.getElements());
        assertSame(PositiveReal.INSTANCE, start.getDomain());
        assertSame(NonNegativeReal.INSTANCE, end.getDomain());
        assertEquals(1.0, start.get(), 0.0);
        assertEquals(0.0, end.get(), 0.0);
    }

    @Test
    void scriptKeepsComposedFixedScalarInputsWorking() throws Exception {
        RealVectorParam<?> result = fromScript("Vector<Real> grid = linspace(start=exp(0.0), end=sqrt(9.0), num=3)");
        assertEquals(List.of(1.0, 2.0, 3.0), result.getElements());
        assertSame(Real.INSTANCE, result.getDomain());
    }

    private static Stream<Arguments> fixedComputedInputs() {
        return Stream.of(
                Arguments.of(
                        "", "start=sum(vector=[-2.0,1.0]),end=sum(vector=[1.0,2.0]),num=3", List.of(-1.0, 1.0, 3.0), 2),
                Arguments.of(
                        "Real first=sum(vector=[-2.0,1.0])\nReal last=sum(vector=[1.0,2.0])\n",
                        "start=first,end=last,num=3",
                        List.of(-1.0, 1.0, 3.0),
                        2),
                Arguments.of(
                        "PositiveReal first=1.0\nNonNegativeReal last=0.0\n",
                        "start=first,end=last,num=3",
                        List.of(1.0, 0.5, 0.0),
                        0));
    }

    @ParameterizedTest
    @MethodSource("fixedComputedInputs")
    void scriptAcceptsFixedComputedEndpointsAndNarrowerDomains(
            String declarations, String arguments, List<Double> expected, int calculations) throws Exception {
        Compiled result = compile(declarations + "Vector<Real> grid=linspace(" + arguments + ")");
        assertEquals(expected, result.vector("grid").getElements());
        assertEquals(calculations, result.state.calculationNodes.size());
    }

    @Test
    void sharedComputedEndpointIsReusedWithoutSamplingState() throws Exception {
        Compiled result = compile("""
                Real boundary=sum(vector=[-1.0,1.0])
                Vector<Real> first=linspace(start=-2.0,end=boundary,num=3)
                Vector<Real> second=linspace(start=boundary,end=2.0,num=3)
                Vector<Real> equal=linspace(start=boundary,end=boundary,num=3)
                Vector<Real> single=linspace(start=boundary,end=2.0,num=1)
                """);
        assertEquals(List.of(-2.0, -1.0, 0.0), result.vector("first").getElements());
        assertEquals(List.of(0.0, 1.0, 2.0), result.vector("second").getElements());
        assertEquals(List.of(0.0, 0.0, 0.0), result.vector("equal").getElements());
        assertEquals(List.of(0.0), result.vector("single").getElements());
        assertEquals(1, result.state.calculationNodes.size());
    }

    @ParameterizedTest
    @CsvSource({"start,false", "end,false", "start,true", "end,true"})
    void stochasticAndStochasticallyDerivedEndpointsRemainUnsupported(String argument, boolean derived) {
        String declarations = derived
                ? "Vector<PositiveReal> values~IID(base=LogNormal(logMean=0.0,logSd=0.5),num=2)\n"
                        + "Real boundary=sum(vector=values)\n"
                : "Real boundary~Normal(mean=1.0,sd=0.5)\n";
        String arguments = argument.equals("start") ? "start=boundary,end=3.0" : "start=0.0,end=boundary";
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> compile(declarations + "Vector<Real> grid=linspace(" + arguments + ",num=3)"));
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
        assertTrue(error.getMessage().contains("'" + argument + "'"), error.getMessage());
    }

    @Test
    void stochasticCountRemainsUnsupported() {
        TileApplicationError error = assertThrows(TileApplicationError.class, () -> compile("""
                        PositiveInteger count~Categorical(probabilities=[0.5,0.5])
                        Vector<Real> grid=linspace(start=0.0,end=1.0,num=count)
                        """));
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
        assertTrue(error.getMessage().contains("'num'"), error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void scriptStillRequiresAPositiveCount(int num) {
        assertThrows(
                TypeError.class, () -> fromScript("Vector<Real> grid = linspace(start=0.0, end=1.0, num=" + num + ")"));
    }

    private static RealVectorParam<?> fromScript(String source) throws Exception {
        Compiled result = compile(source);
        assertTrue(result.state.calculationNodes.isEmpty());
        return result.vector("grid");
    }

    private static Compiled compile(String source) throws Exception {
        var catalog = TileCatalog.select(BEASTState.class, List.of("beast2"));
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        var statements = new EvaluateScalarFunctions()
                .transform(new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse())));
        new TypeResolver(catalog.getComponentResolver()).visitStatements(statements);
        var stochasticity = new StochasticityResolver();
        stochasticity.visitStatements(statements);
        var evaluator = new EvaluateTiles<>(catalog.getTiles(), new VariableResolver(statements), stochasticity);
        var tiles = evaluator.getBestTiling(statements);
        BEASTState state = evaluator.applyBestTiling(new BEASTState("linspace-script-test"));
        Map<String, Object> values = new LinkedHashMap<>();
        for (var tile : tiles) {
            if (tile.getRootNode() instanceof Stmt.Assignment assignment) {
                values.put(assignment.name, tile.apply(state, new IdentityHashMap<>()));
            }
        }
        state.initializeBEASTObjects();
        assertTrue(state.stateNodes.isEmpty());
        assertTrue(state.priorDistributions.isEmpty());
        assertTrue(state.likelihoodDistributions.isEmpty());
        assertTrue(state.operators.isEmpty());
        return new Compiled(state, values);
    }

    private record Compiled(BEASTState state, Map<String, Object> values) {
        RealVectorParam<?> vector(String name) {
            return assertInstanceOf(RealVectorParam.class, values.get(name));
        }
    }

    private static RealVectorParam<Real> linspace(double start, double end, int num) {
        return linspace(new RealScalarParam<>(start, Real.INSTANCE), new RealScalarParam<>(end, Real.INSTANCE), num);
    }

    private static RealVectorParam<Real> linspace(
            RealScalarParam<? extends Real> start, RealScalarParam<? extends Real> end, int num) {
        LinSpaceTile tile = new LinSpaceTile();
        IntScalarParam<PositiveInt> count = new IntScalarParam<>(num, PositiveInt.INSTANCE);
        tile.startInput.setTile(valueTile(start));
        tile.endInput.setTile(valueTile(end));
        tile.numInput.setTile(valueTile(count));
        BEASTState state = new BEASTState("linspace-test");
        RealVectorParam<Real> result = tile.applyTile(state, new IdentityHashMap<>());
        assertEquals(num, result.size());
        assertEquals(num, count.get());
        assertSame(PositiveInt.INSTANCE, count.getDomain());
        assertSame(Real.INSTANCE, result.getDomain());
        assertEquals(new TypeToken<RealVectorParam<Real>>() {}, tile.getTypeToken());
        assertTrue(state.stateNodes.isEmpty());
        assertTrue(state.calculationNodes.isEmpty());
        assertTrue(state.priorDistributions.isEmpty());
        assertTrue(state.likelihoodDistributions.isEmpty());
        assertTrue(state.operators.isEmpty());
        return result;
    }

    private static <T> Tile<T, BEASTState> valueTile(T value) {
        Tile<T, BEASTState> tile = new Tile<T, BEASTState>() {
            @Override
            protected T applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
                return value;
            }
        };
        tile.setIndexVariables(Set.of());
        return tile;
    }
}
