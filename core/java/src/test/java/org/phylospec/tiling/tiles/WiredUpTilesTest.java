package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.PrunedTilingException;

public class WiredUpTilesTest {

    private static final TypeToken<?> COMMON_TYPE = TypeToken.of(Integer.class);
    private static final TypeToken<?> RARE_TYPE = TypeToken.of(String.class);

    private static final int NUM_COMMON_INPUT_TILES = CandidateTile.LIGHTEST_WIRED_UP_TILES + 4;

    /* limit per type */

    @Test
    public void keepsLightestTilesOfEveryTypeIfTypeDependsOnInputs() {
        Set<Tile<?, Object>> wiredUpTiles =
                CandidateTile.runWithMaxWiredUpTiles(() -> this.getWiredUpTiles(new PassThroughTile()));

        assertEquals(CandidateTile.LIGHTEST_WIRED_UP_TILES, this.countTilesOfType(wiredUpTiles, COMMON_TYPE));
        assertEquals(1, this.countTilesOfType(wiredUpTiles, RARE_TYPE));

        // the kept tiles are the lightest ones
        int maxCommonWeight = wiredUpTiles.stream()
                .filter(tile -> tile.getTypeToken().equals(COMMON_TYPE))
                .mapToInt(Tile::getWeight)
                .max()
                .orElseThrow();
        assertEquals(TilePriority.DEFAULT.getWeight() + CandidateTile.LIGHTEST_WIRED_UP_TILES - 1, maxCommonWeight);
    }

    @Test
    public void keepsLightestTilesIfTypeIsFixed() {
        Set<Tile<?, Object>> wiredUpTiles =
                CandidateTile.runWithMaxWiredUpTiles(() -> this.getWiredUpTiles(new FixedTypeTile()));

        assertEquals(CandidateTile.LIGHTEST_WIRED_UP_TILES, wiredUpTiles.size());
    }

    @Test
    public void keepsAllTilesWithoutLimit() {
        assertEquals(
                NUM_COMMON_INPUT_TILES + 1,
                this.getWiredUpTiles(new PassThroughTile()).size());
        assertEquals(
                NUM_COMMON_INPUT_TILES + 1,
                this.getWiredUpTiles(new FixedTypeTile()).size());
    }

    /* pruned alternatives */

    @Test
    public void marksTilesWithPrunedAlternatives() {
        Set<Tile<?, Object>> limitedTiles =
                CandidateTile.runWithMaxWiredUpTiles(() -> this.getWiredUpTiles(new FixedTypeTile()));
        Set<Tile<?, Object>> allTiles = this.getWiredUpTiles(new FixedTypeTile());

        assertTrue(limitedTiles.stream().allMatch(Tile::hasPrunedAlternatives));
        assertFalse(allTiles.stream().anyMatch(Tile::hasPrunedAlternatives));

        // tiles wired up with such tiles have pruned alternatives as well
        Set<Tile<?, Object>> tilesAbove = new FixedTypeTile()
                .getWiredUpTiles(new FixedTypeTile().getTileInputs(), List.of(limitedTiles), new Expr.Call("above"));
        assertTrue(tilesAbove.stream().allMatch(Tile::hasPrunedAlternatives));
    }

    @Test
    public void rejectsTilingIfPrunedAlternativeCouldBeConsistent() {
        assertThrows(
                PrunedTilingException.class,
                () -> CandidateTile.runWithMaxWiredUpTiles(() -> this.getTilesUsingHeaviestSharedTile(Set.of())));

        // with all tiles, the alternative using the heaviest shared tile is found
        assertEquals(1, this.getTilesUsingHeaviestSharedTile(Set.of()).size());
    }

    @Test
    public void marksLighterTilesIfPrunedAlternativeCouldBeConsistent() {
        // this user tile does not use the shared node, so it is consistent with every shared tile
        Tile<?, Object> lightestUserTile = new InputTile(COMMON_TYPE, 0, new Expr.Call("user"));

        Set<Tile<?, Object>> limitedTiles = CandidateTile.runWithMaxWiredUpTiles(
                () -> this.getTilesUsingHeaviestSharedTile(Set.of(lightestUserTile)));

        assertEquals(1, limitedTiles.size());
        assertTrue(limitedTiles.stream().allMatch(Tile::hasPrunedAlternatives));

        // with all tiles, the alternative using the heaviest shared tile is found as well
        Set<Tile<?, Object>> allTiles = this.getTilesUsingHeaviestSharedTile(Set.of(lightestUserTile));

        assertEquals(2, allTiles.size());
        assertFalse(allTiles.stream().anyMatch(Tile::hasPrunedAlternatives));
    }

    /* helpers */

    /**
     * Wires up the candidate tile with more input tiles of a common type than the limit allows, and with a single
     * input tile of a rare type that is heavier than all of them.
     */
    private <C extends Tile<?, Object> & CandidateTile<Object>> Set<Tile<?, Object>> getWiredUpTiles(C candidateTile) {
        AstNode inputNode = new Expr.Call("input");

        Set<Tile<?, Object>> inputTiles = new HashSet<>(this.createCommonInputTiles(inputNode));
        inputTiles.add(new InputTile(RARE_TYPE, NUM_COMMON_INPUT_TILES, inputNode));

        return candidateTile.getWiredUpTiles(candidateTile.getTileInputs(), List.of(inputTiles), new Expr.Call("root"));
    }

    /**
     * Wires up a tile with two inputs: the heaviest tile of a shared node, and the tiles of a user of the shared node.
     * Among the user tiles wired up with a shared tile, only the one using the heaviest shared tile is consistent
     * with it, which is the first one the limit prunes. The given other user tiles are available as well.
     */
    private Set<Tile<?, Object>> getTilesUsingHeaviestSharedTile(Set<Tile<?, Object>> otherUserTiles) {
        List<Tile<?, Object>> sharedTiles = this.createCommonInputTiles(new Expr.Call("shared"));
        Tile<?, Object> heaviestSharedTile = sharedTiles.getLast();

        Set<Tile<?, Object>> userTiles = new FixedTypeTile()
                .getWiredUpTiles(
                        new FixedTypeTile().getTileInputs(),
                        List.of(new HashSet<>(sharedTiles)),
                        new Expr.Call("user"));
        userTiles.addAll(otherUserTiles);

        TwoInputsTile candidateTile = new TwoInputsTile();
        Map<String, Set<Tile<?, Object>>> inputTilesByKey =
                Map.of("shared", Set.of(heaviestSharedTile), "user", userTiles);
        List<TileInput<?, Object>> tileInputs = candidateTile.getTileInputs();

        return candidateTile.getWiredUpTiles(
                tileInputs,
                tileInputs.stream()
                        .map(tileInput -> inputTilesByKey.get(tileInput.getKey()))
                        .toList(),
                new Expr.Call("root"));
    }

    /**
     * Creates more tiles of the common type than the limit allows, lightest first.
     */
    private List<Tile<?, Object>> createCommonInputTiles(AstNode inputNode) {
        List<Tile<?, Object>> inputTiles = new ArrayList<>();
        for (int weight = 0; weight < NUM_COMMON_INPUT_TILES; weight++) {
            inputTiles.add(new InputTile(COMMON_TYPE, weight, inputNode));
        }
        return inputTiles;
    }

    private long countTilesOfType(Set<Tile<?, Object>> tiles, TypeToken<?> typeToken) {
        return tiles.stream()
                .filter(tile -> tile.getTypeToken().equals(typeToken))
                .count();
    }

    /* tiles */

    static final class InputTile extends Tile<Object, Object> {
        private final TypeToken<?> typeToken;

        InputTile(TypeToken<?> typeToken, int weight, AstNode rootNode) {
            this.typeToken = typeToken;
            this.setWeight(weight);
            this.setRootNode(rootNode);
        }

        @Override
        public TypeToken<?> getTypeToken() {
            return this.typeToken;
        }

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return null;
        }
    }

    static final class PassThroughTile extends AstNodeTile<Object, Expr.Call, Object> {
        AstNodeTileInput<Object, Expr.Call, Object> input = new AstNodeTileInput<>("input", call -> call);

        @Override
        public TypeToken<?> getTypeToken() {
            return this.input.getTypeToken();
        }

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return this.input.apply(state, indexVariables);
        }
    }

    static final class FixedTypeTile extends AstNodeTile<Object, Expr.Call, Object> {
        AstNodeTileInput<Object, Expr.Call, Object> input = new AstNodeTileInput<>("input", call -> call);

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return this.input.apply(state, indexVariables);
        }
    }

    static final class TwoInputsTile extends AstNodeTile<Object, Expr.Call, Object> {
        AstNodeTileInput<Object, Expr.Call, Object> sharedInput = new AstNodeTileInput<>("shared", call -> call);
        AstNodeTileInput<Object, Expr.Call, Object> userInput = new AstNodeTileInput<>("user", call -> call);

        @Override
        protected Object applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return this.userInput.apply(state, indexVariables);
        }
    }
}
