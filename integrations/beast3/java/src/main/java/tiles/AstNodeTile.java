package tiles;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.BEASTState;
import tiling.Tile;
import tiling.TypeToken;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Function;

public abstract class AstNodeTile<T, N extends AstNode> extends Tile<T> {
    private N node;

    public abstract Class<N> getTargetNodeType();

    @Override
    protected T applyTile(BEASTState beastState) {
        return this.applyTile(beastState, this.node);
    }

    protected abstract T applyTile(BEASTState beastState, N node);

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, VariableResolver variableResolver, StochasticityResolver stochasticityResolver) {
        if (!this.getTargetNodeType().isAssignableFrom(node.getClass())){
            // node is not of the expected AstNode type
            // we cannot tile this tile
            return Set.of();
        }

        // check the stochasticity

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            return Set.of();
        }

        // the node has the right type and stochasticity

        N narrowedNode = (N) node;

        // the inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST inputs)
        // we use reflection to get the expected inputs

        List<TileInput<N, ?>> inputTiles = new ArrayList<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<N, ?> input = (TileInput<N, ?>) field.get(this);
                    input.resolveTypeFromField(field);
                    inputTiles.add(input);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // for every specified TileInput, we collect the compatible tiles

        List<Set<Tile<?>>> compatibleInputTiles = new ArrayList<>();
        for (TileInput<N, ?> tileInput : inputTiles) {
            compatibleInputTiles.add(
                    tileInput.getCompatibleInputTiles(narrowedNode, allInputTiles)
            );
        }

        // we now look at every combination of the inputs and create a freshly wired up tile

        Set<Tile<?>> wiredUpTiles = new HashSet<>();
        Utils.visitCombinations(
                compatibleInputTiles,
                inputs -> {
                    AstNodeTile<?, N> wiredUpTile = (AstNodeTile<?, N>) this.createInstance();
                    wiredUpTile.setNode(narrowedNode);

                    // get TileInput fields from the fresh instance in declaration order

                    List<TileInput<N, ?>> freshInputs = new ArrayList<>();
                    for (Field field : wiredUpTile.getClass().getDeclaredFields()) {
                        if (field.getType().equals(TileInput.class)) {
                            field.setAccessible(true);
                            try {
                                freshInputs.add((TileInput<N, ?>) field.get(wiredUpTile));
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    // wire each input tile and accumulate weight

                    int totalWeight = this.getPriority().getWeight();
                    for (int i = 0; i < freshInputs.size(); i++) {
                        Tile<?> inputTile = inputs.get(i);
                        freshInputs.get(i).setTile(inputTile);
                        totalWeight += inputTile.getWeight();
                    }

                    wiredUpTile.setWeight(totalWeight);
                    wiredUpTiles.add(wiredUpTile);
                }
        );

        return wiredUpTiles;
    }

    private void setNode(N node) {
        this.node = node;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getClass().getSimpleName());
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<?, ?> input = (TileInput<?, ?>) field.get(this);
                    Tile<?> child = input.getTile();
                    if (child != null) {
                        sb.append(" ").append(child);
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        sb.append(")");
        return sb.toString();
    }

    public static class TileInput<N, O> {
        private final Function<N, AstNode> getter;
        private TypeToken<O> expectedTypeToken;

        private Tile<O> tile;

        public TileInput(Function<N, AstNode> getter) {
            this.getter = getter;
        }

        // called during reflection-based field scanning in tryToTile to resolve
        // the type token from the field's generic signature rather than requiring
        // it to be passed explicitly at the call site
        void resolveTypeFromField(Field field) {
            if (this.expectedTypeToken != null) return;
            // TileInput<N, O> — O is the second type argument
            ParameterizedType fieldType = (ParameterizedType) field.getGenericType();
            this.expectedTypeToken = (TypeToken<O>) TypeToken.of(fieldType.getActualTypeArguments()[1]);
        }

        public Set<Tile<?>> getCompatibleInputTiles(N astNode, Map<AstNode, Set<Tile<?>>> inputTiles) {
            AstNode inputAstNode = this.getter.apply(astNode);
            Set<Tile<?>> potentialInputs = inputTiles.get(inputAstNode);

            Set<Tile<?>> compatibleInputs = new HashSet<>();
            for (Tile<?> potentialInput : potentialInputs) {
                if (expectedTypeToken.isAssignableFrom(potentialInput.getTypeToken())) {
                    compatibleInputs.add(potentialInput);
                }
            }

            return compatibleInputs;
        }

        public void setTile(Tile<?> tile) {
            this.tile = (Tile<O>) tile;
        }

        public Tile<O> getTile() {
            return this.tile;
        }

        public O apply(BEASTState beastState) {
            return this.tile.apply(beastState);
        }

        public TypeToken<?> getTypeToken() {
            return this.tile != null ? this.tile.getTypeToken() : expectedTypeToken;
        }
    }
}
