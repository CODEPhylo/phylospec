package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.BoolScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;

public class LiteralTileTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void includesUnitIntervalCandidateAtDoubleEndpoints(double value) throws FailedTilingAttempt {
        assertTrue(hasUnitIntervalCandidate(value));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1})
    void excludesUnitIntervalCandidateOutsideItsDomain(double value) throws FailedTilingAttempt {
        assertFalse(hasUnitIntervalCandidate(value));
    }

    @Test
    public void createsBooleanAndBoolScalarTiles() throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> tiles = new LiteralTile<>().tryToTile(new Expr.Literal(true), Map.of(), null, null);

        Set<Object> values = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Tile<?, BEASTState> tile : tiles) {
            values.add(tile.apply(new BEASTState("boolean-literal"), new IdentityHashMap<>()));
        }

        assertEquals(2, values.size());
        assertTrue(values.contains(Boolean.TRUE));
        assertTrue(values.stream()
                .filter(BoolScalar.class::isInstance)
                .map(BoolScalar.class::cast)
                .anyMatch(BoolScalar::get));
    }

    private static boolean hasUnitIntervalCandidate(double expected) throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> candidates =
                new LiteralTile<>().tryToTile(new Expr.Literal(expected), Map.of(), null, null);
        for (Tile<?, BEASTState> candidate : candidates) {
            Object value = candidate.apply(new BEASTState("probability-literal"), new IdentityHashMap<>());
            if (value instanceof RealScalarParam<?> scalar && scalar.getDomain() == UnitInterval.INSTANCE) {
                assertEquals(expected, scalar.get(), 0.0);
                return true;
            }
        }
        return false;
    }
}
