package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;

public class WiredUpTilesTest {

    private static final TypeToken<?> COMMON_TYPE = TypeToken.of(Integer.class);
    private static final TypeToken<?> RARE_TYPE = TypeToken.of(String.class);

    private static final int NUM_COMMON_INPUT_TILES = CandidateTile.LIGHTEST_WIRED_UP_TILES + 4;

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

    /* helpers */

    /**
     * Wires up the candidate tile with more input tiles of a common type than the limit allows, and with a single
     * input tile of a rare type that is heavier than all of them.
     */
    private <C extends Tile<?, Object> & CandidateTile<Object>> Set<Tile<?, Object>> getWiredUpTiles(C candidateTile) {
        AstNode inputNode = new Expr.Call("input");

        Set<Tile<?, Object>> inputTiles = new HashSet<>();
        for (int weight = 0; weight < NUM_COMMON_INPUT_TILES; weight++) {
            inputTiles.add(new InputTile(COMMON_TYPE, weight, inputNode));
        }
        inputTiles.add(new InputTile(RARE_TYPE, NUM_COMMON_INPUT_TILES, inputNode));

        return candidateTile.getWiredUpTiles(candidateTile.getTileInputs(), List.of(inputTiles), new Expr.Call("root"));
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
}
