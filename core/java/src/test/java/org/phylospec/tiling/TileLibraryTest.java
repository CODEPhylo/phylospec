package org.phylospec.tiling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;

public class TileLibraryTest {

    @Test
    public void earlierLibraryOverridesEquivalentMapping() {
        List<CandidateTile<Object>> tiles = TileLibrary.combine(List.of(
                new TestLibrary("package", List.of(new PackageTile())),
                new TestLibrary("base", List.of(new BaseTile(), new OverloadTile()))));

        assertEquals(List.of(PackageTile.class, OverloadTile.class), classesOf(tiles));
    }

    @Test
    public void retainsDuplicateMappingsWithinOneLibrary() {
        List<CandidateTile<Object>> tiles =
                TileLibrary.combine(List.of(new TestLibrary("broken", List.of(new PackageTile(), new BaseTile()))));

        assertEquals(List.of(PackageTile.class, BaseTile.class), classesOf(tiles));
    }

    private static List<Class<?>> classesOf(List<CandidateTile<Object>> tiles) {
        List<Class<?>> classes = new ArrayList<>();
        for (CandidateTile<Object> tile : tiles) {
            classes.add(tile.getClass());
        }
        return classes;
    }

    private static final class TestLibrary extends TileLibrary<Object> {
        private final String id;
        private final List<CandidateTile<Object>> tiles;

        private TestLibrary(String id, List<CandidateTile<Object>> tiles) {
            this.id = id;
            this.tiles = tiles;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Class<Object> getStateType() {
            return Object.class;
        }

        @Override
        public List<CandidateTile<Object>> getTiles() {
            return tiles;
        }
    }

    private abstract static class TestTile extends GeneratorTile<String, Object> {
        @Override
        public TypeToken<?> getTypeToken() {
            return TypeToken.of(String.class);
        }

        @Override
        public String getPhyloSpecGeneratorName() {
            return "example";
        }

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return "example";
        }
    }

    private static final class PackageTile extends TestTile {}

    private static final class BaseTile extends TestTile {}

    private static final class OverloadTile extends TestTile {
        GeneratorTileInput<Integer, Object> valueInput = new GeneratorTileInput<>("value");
    }
}
