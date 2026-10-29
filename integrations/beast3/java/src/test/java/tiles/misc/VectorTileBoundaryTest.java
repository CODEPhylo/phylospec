package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import beast.base.spec.domain.Int;
import beast.base.spec.domain.NonNegativeInt;
import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.parameter.IntVectorParam;
import beastconfig.BEASTState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
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

    private static void assertIntegerVectors(List<Integer> expectedValues, Int... expectedDomains)
            throws FailedTilingAttempt {
        List<Expr> elements = new ArrayList<>();
        for (int value : expectedValues) {
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

        Set<Tile<?, BEASTState>> candidates = new VectorTile<>().tryToTile(array, inputTiles, variables, stochasticity);

        Map<TypeToken<?>, IntVectorParam<?>> actual = new HashMap<>();
        BEASTState state = new BEASTState("vector-boundary-test");

        for (Tile<?, BEASTState> candidate : candidates) {
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
