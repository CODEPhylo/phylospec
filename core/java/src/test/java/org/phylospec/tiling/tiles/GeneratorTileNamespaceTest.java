package org.phylospec.tiling.tiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

public class GeneratorTileNamespaceTest {

    @Test
    public void acceptsTileFromResolvedNamespace() throws FailedTilingAttempt {
        Expr.Call call = resolvedCall("package.one");

        assertFalse(new TestTile("package.one")
                .tryToTile(call, Map.of(), new VariableResolver(List.of()), stochasticity(call))
                .isEmpty());
    }

    @Test
    public void rejectsTileFromDifferentNamespace() {
        Expr.Call call = resolvedCall("package.one");

        assertThrows(FailedTilingAttempt.Irrelevant.class, () -> new TestTile("package.two")
                .tryToTile(call, Map.of(), new VariableResolver(List.of()), stochasticity(call)));
    }

    private static Expr.Call resolvedCall(String namespace) {
        Expr.Call call = new Expr.Call("example");
        call.setResolvedNamespace(namespace);
        return call;
    }

    private static StochasticityResolver stochasticity(Expr.Call call) {
        StochasticityResolver resolver = new StochasticityResolver();
        call.accept(resolver);
        return resolver;
    }

    static final class TestTile extends GeneratorTile<String, Object> {
        private final String namespace;

        TestTile() {
            this("package.one");
        }

        TestTile(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public String getPhyloSpecGeneratorName() {
            return "example";
        }

        @Override
        public Optional<String> getNamespace() {
            return Optional.of(namespace);
        }

        @Override
        public TypeToken<?> getTypeToken() {
            return TypeToken.of(String.class);
        }

        @Override
        protected String applyTile(Object state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
            return "example";
        }
    }
}
