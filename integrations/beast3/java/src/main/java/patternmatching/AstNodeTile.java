package patternmatching;

import org.phylospec.Utils;
import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Function;

public abstract class AstNodeTile<T, N extends AstNode> extends Tile<T> {
    public abstract Class<N> getTargetNodeType();

    @Override
    public Set<Tile<?>> tryToTile(AstNode node, Map<AstNode, Set<Tile<?>>> allInputTiles, TypeResolver typeResolver, VariableResolver variableResolver) {
        if (!this.getTargetNodeType().isAssignableFrom(node.getClass())){
            // node is not of the expected AstNode type
            // we cannot tile this tile
            return Set.of();
        }

        // the node has the right type

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
                    Tile<?> wiredUpTile = this.createInstance();

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

        public O apply(BEASTState beastState) {
            return this.tile.applyTile(beastState);
        }

        public TypeToken<?> getTypeToken() {
            return this.tile != null ? this.tile.getTypeToken() : expectedTypeToken;
        }
    }
}
