package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beastconfig.BEASTState;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.tiles.Tile;

public class ListVectorTileTest {

    @Test
    public void filtersLongVectorsByElementTypeBeforeCombining() throws FailedTilingAttempt {
        Expr.Literal element = new Expr.Literal(0.0625);
        Expr.Array array = new Expr.Array(Collections.nCopies(16, element));
        Set<Tile<?, BEASTState>> elementTiles = new LiteralTile<>().tryToTile(element, Map.of(), null, null);
        Map<AstNode, Set<Tile<?, BEASTState>>> inputTiles = Map.of(element, elementTiles);

        Set<Tile<?, BEASTState>> vectorTiles = new ListVectorTile().tryToTile(array, inputTiles, null, null);

        assertEquals(elementTiles.size(), vectorTiles.size());
        Set<TypeToken<?>> elementTypes =
                elementTiles.stream().map(Tile::getTypeToken).collect(java.util.stream.Collectors.toSet());
        assertTrue(vectorTiles.stream().map(Tile::getTypeToken).allMatch(vectorType -> elementTypes.stream()
                .map(TypeToken::listOf)
                .anyMatch(vectorType::equals)));
    }

    @Test
    public void preservesMultipleCombinationsWithinTheSameElementType() throws FailedTilingAttempt {
        Expr.Literal first = new Expr.Literal(1.0);
        Expr.Literal second = new Expr.Literal(2.0);
        Expr.Array array = new Expr.Array(List.of(first, second));
        TypeToken<Double> doubleType = new TypeToken<>() {};

        Set<Tile<?, BEASTState>> firstTiles =
                Set.of(new LiteralTile<>(doubleType, 1.0, first), new LiteralTile<>(doubleType, 10.0, first));
        Set<Tile<?, BEASTState>> secondTiles =
                Set.of(new LiteralTile<>(doubleType, 2.0, second), new LiteralTile<>(doubleType, 20.0, second));

        Set<Tile<?, BEASTState>> vectorTiles =
                new ListVectorTile().tryToTile(array, Map.of(first, firstTiles, second, secondTiles), null, null);

        assertEquals(4, vectorTiles.size());
    }
}
