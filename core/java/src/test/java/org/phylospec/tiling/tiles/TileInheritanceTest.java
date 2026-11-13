package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.IdentityHashMap;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;

public class TileInheritanceTest {

    @Test
    public void discoversInputsDeclaredInSuperclasses() {
        assertEquals(1, new Concrete().getGeneratorTileInputs().size());
    }

    @Test
    public void resolvesReturnTypeThroughIntermediateSuperclasses() {
        assertEquals(TypeToken.of(String.class), new Concrete().getTypeToken());
    }

    abstract static class Base<T> extends GeneratorTile<T, Object> {
        GeneratorTileInput<String, Object> sharedInput = new GeneratorTileInput<>("shared");

        @Override
        public String getPhyloSpecGeneratorName() {
            return "example";
        }
    }

    static final class Concrete extends Base<String> {
        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return "example";
        }
    }
}
