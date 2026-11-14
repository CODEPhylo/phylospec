package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;

public class TileInheritanceTest {

    @Test
    public void discoversInputsDeclaredInSuperclasses() {
        assertEquals(2, new Concrete().getGeneratorTileInputs().size());
    }

    @Test
    public void resolvesReturnTypeThroughIntermediateSuperclasses() {
        assertEquals(TypeToken.of(String.class), new Concrete().getTypeToken());
    }

    @Test
    public void resolvesInputTypeVariablesOfSuperclasses() {
        List<TypeToken<?>> inputTypes = new Concrete()
                .getGeneratorTileInputs().stream().map(TileInput::getTypeToken).toList();

        assertEquals(List.of(TypeToken.of(String.class), TypeToken.of(String.class)), inputTypes);
    }

    @Test
    public void resolvesTargetNodeTypeThroughIntermediateSuperclasses() {
        assertEquals(Expr.Binary.class, new ConcreteAstNodeSubclass().getTargetNodeType());
    }

    abstract static class Base<T> extends GeneratorTile<T, Object> {
        GeneratorTileInput<String, Object> sharedInput = new GeneratorTileInput<>("shared");
        GeneratorTileInput<T, Object> genericInput = new GeneratorTileInput<>("generic");

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

    static class ConcreteAstNode extends AstNodeTile<String, Expr.Binary, Object> {
        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return "example";
        }
    }

    static final class ConcreteAstNodeSubclass extends ConcreteAstNode {}
}
