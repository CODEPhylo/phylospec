package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;

public abstract class AstNodeTile<T, N extends AstNode> extends Tile<T> {
    public abstract Class<N> getTargetNodeType();

    private N matchedNode;

    @Override
    public Set<Tile<T>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> inputTiles, TypeResolver typeResolver, VariableResolver variableResolver) {
        if (!this.getTargetNodeType().isAssignableFrom(node.getClass())) return Set.of();

        N narrowedNode = (N) node;

        // check all inputs can be satisfied before allocating a fresh instance
        for (TileInput<N, ?> input : this.getInputs()) {
            if (!input.canBeApplied(narrowedNode, inputTiles)) return Set.of();
        }

        // create a fresh instance and wire it up
        AstNodeTile<T, N> freshTile = (AstNodeTile<T, N>) this.createInstance();
        freshTile.matchedNode = narrowedNode;

        int inputWeight = 0;
        for (TileInput<N, ?> freshInput : freshTile.getInputs()) {
            inputWeight += freshInput.set(narrowedNode, inputTiles);
        }

        freshTile.setWeight(inputWeight + this.getPriority().getWeight());

        return Set.of(freshTile);
    }

    @Override
    public T applyTile(BEASTState beastState) {
        return this.applyTile(this.matchedNode, beastState);
    }

    public abstract T applyTile(N node, BEASTState beastState);

    private Set<TileInput<N, ?>> getInputs() {
        Set<TileInput<N, ?>> inputs = new HashSet<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    inputs.add((TileInput<N, ?>) field.get(this));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return inputs;
    }

    public static class TileInput<N, O> {
        private final Function<N, AstNode> getter;
        private final TypeToken<O> expectedTypeToken;

        private Tile<O> tile;

        public TileInput(Function<N, AstNode> getter, TypeToken<O> typeToken) {
            this.getter = getter;
            this.expectedTypeToken = typeToken;
        }

        public boolean canBeApplied(N astNode, Map<AstNode, Set<Tile<?>>> inputTiles) {
            AstNode inputAstNode = this.getter.apply(astNode);
            Set<Tile<?>> potentialInputs = inputTiles.get(inputAstNode);
            return getBestTile(potentialInputs) != null;
        }

        public int set(N astNode, Map<AstNode, Set<Tile<?>>> inputTiles) {
            AstNode inputAstNode = this.getter.apply(astNode);
            Set<Tile<?>> potentialInputs = inputTiles.get(inputAstNode);
            this.tile = getBestTile(potentialInputs);
            return this.tile.getWeight();
        }

        public O apply(BEASTState beastState) {
            return this.tile.applyTile(beastState);
        }

        private Tile<O> getBestTile(Set<Tile<?>> potentialInputs) {
            if (potentialInputs == null || potentialInputs.isEmpty()) return null;

            int lowestWeight = Integer.MAX_VALUE;
            Tile<O> bestTile = null;

            for (Tile<?> candidate : potentialInputs) {
                if (expectedTypeToken.isAssignableFrom(candidate.getGeneratedType()) && candidate.getWeight() < lowestWeight) {
                    lowestWeight = candidate.getWeight();
                    bestTile = (Tile<O>) candidate;
                }
            }

            return bestTile;
        }
    }
}
