package tiles.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import beast.base.spec.domain.Int;
import beast.base.spec.inference.parameter.IntScalarParam;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.Tile;

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
