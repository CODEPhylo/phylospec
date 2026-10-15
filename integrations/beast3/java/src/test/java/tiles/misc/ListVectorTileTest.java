package tiles.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beastconfig.BEASTState;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.Tile;

public class ListVectorTileTest {

    @Test
    public void exposesSharedElementToGlobalConsistencyCheck() throws InconsistentTilingException {
        Expr.Literal node = new Expr.Literal(1.0);
        Tile<?, BEASTState> first = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node);
        Tile<?, BEASTState> alternative = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node);
        ListVectorTile vector = new ListVectorTile(List.of(first, first));

        assertSame(first, vector.getWiredUpInputs().get(node));
        assertEquals(1, vector.getWiredUpInputs().size());
        assertFalse(vector.isInconsistent(new IdentityHashMap<>()));
        IdentityHashMap<AstNode, Tile<?, ?>> assignments = new IdentityHashMap<>();
        assignments.put(node, alternative);
        assertTrue(vector.isInconsistent(assignments));
    }

    @Test
    public void rejectsDifferentTilesForRepeatedElement() {
        Expr.Literal node = new Expr.Literal(1.0);
        ListVectorTile vector = new ListVectorTile(List.of(
                new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node),
                new LiteralTile<>(new TypeToken<Double>() {}, 1.0, node)));

        assertThrows(InconsistentTilingException.class, vector::getWiredUpInputs);
        assertTrue(vector.isInconsistent(new IdentityHashMap<>()));
    }

    @Test
    public void checksSharedDependenciesAcrossNestedVectors() {
        Expr.Literal shared = new Expr.Literal(1.0);
        Tile<?, BEASTState> first = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, shared);
        Tile<?, BEASTState> alternative = new LiteralTile<>(new TypeToken<Double>() {}, 1.0, shared);
        ListVectorTile left = new ListVectorTile(List.of(first));
        left.setRootNode(new Expr.Array(List.of(shared)));
        ListVectorTile right = new ListVectorTile(List.of(alternative));
        right.setRootNode(new Expr.Array(List.of(shared)));

        assertTrue(new ListVectorTile(List.of(left, right)).isInconsistent(new IdentityHashMap<>()));
        assertFalse(new ListVectorTile(List.of(left, left)).isInconsistent(new IdentityHashMap<>()));
    }

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
