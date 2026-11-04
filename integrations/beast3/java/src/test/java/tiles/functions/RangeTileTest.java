package tiles.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beast.base.spec.inference.parameter.IntVectorParam;
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
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class RangeTileTest {

    @Test
    void includesBothEndpoints() {
        assertEquals(List.of(1, 2, 3, 4, 5), range(1, 5));
        assertEquals(List.of(5, 4, 3, 2, 1), range(5, 1));
        assertEquals(List.of(5), range(5, 5));
        assertEquals(List.of(-2, -1, 0, 1), range(-2, 1));
    }

    @Test
    void handlesEndpointsNearIntegerLimits() {
        assertEquals(
                List.of(Integer.MIN_VALUE, Integer.MIN_VALUE + 1), range(Integer.MIN_VALUE, Integer.MIN_VALUE + 1));

        assertEquals(
                List.of(Integer.MAX_VALUE, Integer.MAX_VALUE - 1), range(Integer.MAX_VALUE, Integer.MAX_VALUE - 1));

        assertEquals(List.of(Integer.MIN_VALUE), range(Integer.MIN_VALUE, Integer.MIN_VALUE));

        assertEquals(List.of(Integer.MAX_VALUE), range(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void rejectsLengthsThatCannotFitInAnIntegerIndexedArray() {
        assertThrows(TileApplicationError.class, () -> range(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    private static Stream<Arguments> fixedInputs() {
        return Stream.of(
                Arguments.of("", "start=-1,end=3", List.of(-1, 0, 1, 2, 3), 0),
                Arguments.of(
                        "PositiveInteger first=1\nNonNegativeInteger last=0\n",
                        "start=first,end=last",
                        List.of(1, 0),
                        0),
                Arguments.of("", "start=sum(vector=[-2,1]),end=sum(vector=[1,2])", List.of(-1, 0, 1, 2, 3), 2),
                Arguments.of(
                        "Integer first=sum(vector=[-2,1])\nInteger last=sum(vector=[1,2])\n",
                        "start=first,end=last",
                        List.of(-1, 0, 1, 2, 3),
                        2));
    }

    @ParameterizedTest
    @MethodSource("fixedInputs")
    void scriptAcceptsFixedLiteralsParametersAndComputedEndpoints(
            String declarations, String arguments, List<Integer> expected, int calculations) throws Exception {
        var result = fromScript(declarations + "Vector<Integer> values=range(" + arguments + ")");
        assertEquals(expected, result.vector("values").getElements());
        assertEquals(calculations, result.state.calculationNodes.size());
    }

    @Test
    void sharedComputedEndpointIsReusedWithoutSamplingState() throws Exception {
        var result = fromScript("""
                Integer boundary=sum(vector=[-1,1])
                Vector<Integer> first=range(start=-1,end=boundary)
                Vector<Integer> second=range(start=boundary,end=1)
                Vector<Integer> single=range(start=boundary,end=boundary)
                """);
        assertEquals(List.of(-1, 0), result.vector("first").getElements());
        assertEquals(List.of(0, 1), result.vector("second").getElements());
        assertEquals(List.of(0), result.vector("single").getElements());
        assertEquals(1, result.state.calculationNodes.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"start=boundary,end=5", "start=0,end=boundary"})
    void stochasticEndpointsRemainUnsupported(String arguments) {
        TileApplicationError error = assertThrows(
                TileApplicationError.class,
                () -> fromScript("Integer boundary~DiscreteUniform(lower=1,upper=3)\n" + "Vector<Integer> values=range("
                        + arguments + ")"));
        assertTrue(error.getMessage().contains("random variable"), error.getMessage());
    }

    private static Compiled fromScript(String source) throws Exception {
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
        BEASTState state = evaluator.applyBestTiling(new BEASTState("range-script-test"));
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
        IntVectorParam<?> vector(String name) {
            return assertInstanceOf(IntVectorParam.class, values.get(name));
        }
    }

    private static List<Integer> range(int start, int end) {
        RangeTile tile = new RangeTile();
        tile.startInput.setTile(valueTile(new IntScalarParam<>(start, Int.INSTANCE)));
        tile.endInput.setTile(valueTile(new IntScalarParam<>(end, Int.INSTANCE)));

        return tile.applyTile(new BEASTState("range-test"), new IdentityHashMap<>())
                .getElements();
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
