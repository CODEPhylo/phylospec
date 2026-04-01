package patternmatching;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.TypeResolver;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AstNodeTile<T extends AstNode> extends Tile {
    public abstract Class<T> getTargetNodeType();

    @Override
    public Set<EvaluatedTile> tryToTile(AstNode node, Map<AstNode, Set<EvaluatedTile>> inputTiles, TypeResolver typeResolver) {
        if (this.getTargetNodeType().isAssignableFrom(node.getClass())) {
            T narrowedNode = (T) node;

            Set<TileInput<T, ?>> inputs = this.getInputs();

            // check if all inputs can be applied

            int inputWeight = 0;

            for (TileInput<T, ?> input : inputs) {
                if (input.canBeApplied(narrowedNode, inputTiles)) {
                    // set the input value
                    inputWeight += input.set(narrowedNode, inputTiles);
                } else {
                    // we cannot find a matching tile for this input
                    // we cannot apply this tile
                    return Set.of();
                }
            }

            // we can apply all inputs and have set them
            // let's tile

            Set<EvaluatedTile> evaluatedTiles = applyTile((T) node);

            // update weights

            int totalWeight = inputWeight + this.getPriority().getWeight();
            return evaluatedTiles.stream().map(t -> t.withWeight(totalWeight)).collect(Collectors.toSet());
        } else {
            return Set.of();
        }
    }

    abstract public Set<EvaluatedTile> applyTile(T astNode);

    private Set<TileInput<T, ?>> getInputs() {
        // the expected inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST inputs)
        // we use reflection to get the expected inputs
        Set<TileInput<T, ?>> inputs = new HashSet<>();

        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getType().equals(TileInput.class)) {
                field.setAccessible(true);
                try {
                    TileInput<T, ?> input = (TileInput<T, ?>) field.get(this);
                    inputs.add(input);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return inputs;
    }

    public static class TileInput<T, O> {
        private final Function<T, AstNode> getter;
        private final TypeToken<O> typeToken;

        O value;

        public TileInput(Function<T, AstNode> getter, TypeToken<O> typeToken) {
            this.getter = getter;
            this.typeToken = typeToken;
        }

        public boolean canBeApplied(T astNode, Map<AstNode, Set<EvaluatedTile>> inputTiles) {
            AstNode inputAstNode = this.getter.apply(astNode);
            Set<EvaluatedTile> potentialInputs = inputTiles.get(inputAstNode);
            EvaluatedTile bestInput = TileUtils.getBestInput(potentialInputs, this.typeToken);
            return bestInput != null;
        }

        public int set(T astNode, Map<AstNode, Set<EvaluatedTile>> inputTiles) {
            AstNode inputAstNode = this.getter.apply(astNode);
            Set<EvaluatedTile> potentialInputs = inputTiles.get(inputAstNode);
            EvaluatedTile bestInputTile = TileUtils.getBestInput(potentialInputs, this.typeToken);
            this.value = (O) bestInputTile.generatedObject();
            return bestInputTile.weight();
        }

        public O get() {
            return this.value;
        }

    }
}
