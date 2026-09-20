package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beast.base.spec.type.BoolScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;

public class LiteralTileTest {

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
