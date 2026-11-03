package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.IntVectorParam;
import beast.base.spec.inference.parameter.RealVectorParam;
import beast.base.spec.inference.parameter.SimplexParam;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import runner.PhyloSpecRunner;

class VectorTileBoundaryTest {

    @Test
    void supportsBothIntegerEndpoints() throws FailedTilingAttempt {
        assertIntegerVectors(List.of(Integer.MIN_VALUE, Integer.MAX_VALUE), Int.INSTANCE);
    }

    @Test
    void preservesApplicableIntegerDomainsAtMaximum() throws FailedTilingAttempt {
        assertIntegerVectors(List.of(Integer.MAX_VALUE), Int.INSTANCE, NonNegativeInt.INSTANCE, PositiveInt.INSTANCE);

        assertIntegerVectors(List.of(0, Integer.MAX_VALUE), Int.INSTANCE, NonNegativeInt.INSTANCE);
    }

    @Test
    void keepsOrdinaryIntegerArraysWorking() throws FailedTilingAttempt {
        assertIntegerVectors(List.of(-2, 0, 3), Int.INSTANCE);

        assertIntegerVectors(List.of(1, 2, 3), Int.INSTANCE, NonNegativeInt.INSTANCE, PositiveInt.INSTANCE);
    }

    @Test
    void boundaryScriptPassesCheck() throws Exception {
        Path source = Path.of("src/test/java/tiling/vectors/integerVectorBoundary.phylospec");

        assertNotNull(new PhyloSpecRunner(source, List.of("beast2")).check());
    }

    @ParameterizedTest
    @CsvSource({"1.0, 0.0000001", "0.9999999, 0.0"})
    void nearSimplexArraysRemainOrdinaryRealVectors(double first, double second) throws FailedTilingAttempt {
        List<Double> expected = List.of(first, second);
        Map<TypeToken<?>, Object> values = realVectorCandidates(expected);
        Object ordinary = values.get(TypeToken.parameterized(RealVectorParam.class, Real.class));

        assertTrue(ordinary instanceof RealVectorParam<?>);
        assertEquals(expected, ((RealVectorParam<?>) ordinary).getElements());
        assertFalse(values.values().stream().anyMatch(SimplexParam.class::isInstance));
    }

    @Test
    void preservesSimplexCandidateWithDoubleEndpoints() throws FailedTilingAttempt {
        List<Double> expected = List.of(0.0, 1.0, 0.0);
        Map<TypeToken<?>, Object> values = realVectorCandidates(expected);
        Object simplex = values.get(TypeToken.of(SimplexParam.class));

        assertTrue(simplex instanceof SimplexParam);
        assertEquals(expected, ((SimplexParam) simplex).getElements());
    }

    private static Map<TypeToken<?>, Object> realVectorCandidates(List<Double> expectedValues)
            throws FailedTilingAttempt {
        Map<TypeToken<?>, Object> values = new HashMap<>();
        BEASTState state = new BEASTState("real-vector-boundary-test");
        for (Tile<?, BEASTState> candidate : vectorCandidates(expectedValues)) {
            candidate.setIndexVariables(Set.of());
            values.put(candidate.getTypeToken(), candidate.apply(state, new IdentityHashMap<>()));
        }
        return values;
    }

    private static Set<Tile<?, BEASTState>> vectorCandidates(List<? extends Number> expectedValues)
            throws FailedTilingAttempt {
        List<Expr> elements = new ArrayList<>();
        for (Number value : expectedValues) {
            elements.add(new Expr.Literal(value));
        }

        Expr.Array array = new Expr.Array(elements);
        VariableResolver variables = new VariableResolver(List.of(array));
        StochasticityResolver stochasticity = new StochasticityResolver();
        array.accept(stochasticity);

        Map<AstNode, Set<Tile<?, BEASTState>>> inputTiles = new IdentityHashMap<>();
        for (Expr element : elements) {
            Set<Tile<?, BEASTState>> candidates =
                    new LiteralTile<>().tryToTile(element, Map.of(), variables, stochasticity);
            candidates.forEach(tile -> tile.setIndexVariables(Set.of()));
            inputTiles.put(element, candidates);
        }

        return new VectorTile<>().tryToTile(array, inputTiles, variables, stochasticity);
    }

    private static void assertIntegerVectors(List<Integer> expectedValues, Int... expectedDomains)
            throws FailedTilingAttempt {
        Map<TypeToken<?>, IntVectorParam<?>> actual = new HashMap<>();
        BEASTState state = new BEASTState("vector-boundary-test");

        for (Tile<?, BEASTState> candidate : vectorCandidates(expectedValues)) {
            candidate.setIndexVariables(Set.of());
            Object value = candidate.apply(state, new IdentityHashMap<>());

            if (value instanceof IntVectorParam<?> vector) {
                assertNull(actual.put(candidate.getTypeToken(), vector), "Duplicate integer vector type");
            }
        }

        assertEquals(expectedDomains.length, actual.size());

        for (Int domain : expectedDomains) {
            TypeToken<?> expectedType = TypeToken.parameterized(IntVectorParam.class, domain.getClass());
            IntVectorParam<?> vector = actual.get(expectedType);

            assertNotNull(vector);
            assertSame(domain, vector.getDomain());
            assertEquals(expectedValues.size(), vector.size());
            assertEquals(expectedValues, vector.getElements());
            assertEquals(domain.getLower(), vector.getLower());
            assertEquals(domain.getUpper(), vector.getUpper());
        }
    }
}
