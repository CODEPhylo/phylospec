package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.domain.UnitInterval;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.BoolScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;

public class LiteralTileTest {

    static Stream<Number> probabilities() {
        return Stream.of(0, 1, 0.0, 1.0, 0.25, 0.75);
    }

    @ParameterizedTest
    @MethodSource("probabilities")
    void createsUnitIntervalTileIncludingBothEndpoints(Number value) throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> tiles = new LiteralTile<>().tryToTile(new Expr.Literal(value), Map.of(), null, null);
        TypeToken<RealScalarParam<UnitInterval>> expected = new TypeToken<>() {};
        Tile<?, BEASTState> probability = tiles.stream()
                .filter(tile -> expected.isAssignableFrom(tile.getTypeToken()))
                .findFirst()
                .orElseThrow();
        RealScalarParam<?> parameter =
                (RealScalarParam<?>) probability.apply(new BEASTState("probability-literal"), new IdentityHashMap<>());
        assertEquals(value.doubleValue(), parameter.get());
    }

    static Stream<Number> outsideUnitInterval() {
        return Stream.of(-1, 2, -0.01, 1.01, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("outsideUnitInterval")
    void doesNotCreateUnitIntervalTileForInvalidValues(Number value) throws FailedTilingAttempt {
        Set<Tile<?, BEASTState>> tiles = new LiteralTile<>().tryToTile(new Expr.Literal(value), Map.of(), null, null);
        TypeToken<RealScalarParam<UnitInterval>> expected = new TypeToken<>() {};
        assertFalse(tiles.stream().anyMatch(tile -> expected.isAssignableFrom(tile.getTypeToken())));
    }

    @Test
    void rejectsNaNWhenConstructingTheNumericDomain() {
        assertThrows(IllegalArgumentException.class, () -> new LiteralTile<>()
                .tryToTile(new Expr.Literal(Double.NaN), Map.of(), null, null));
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
}
