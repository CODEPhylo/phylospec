package tiles;

import org.phylospec.ast.AstNode;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;
import tiling.FailedTilingAttempt;
import tiling.Tile;
import tiling.TileFactory;
import tiling.TileInput;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Function;

/**
 * This class represents tiles that cover a single AstNode of type N. Extend this class for custom tiles.
 * Use AstNodeTileInput fields to specify the tile inputs (similar to BEAST 2.8 inputs).
 */
public abstract class AstNodeTile<T, N extends AstNode> extends Tile<T> implements TileFactory {

    public Class<N> getTargetNodeType() {
        return (Class<N>) ((ParameterizedType) getClass().getGenericSuperclass())
                .getActualTypeArguments()[1];
    }

    @Override
    public Set<Tile<?>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver
    ) throws FailedTilingAttempt {
        if (!this.getTargetNodeType().isAssignableFrom(node.getClass())) {
            // node is not of the expected AstNode type
            // we cannot tile this tile
            throw new FailedTilingAttempt.Irrelevant();
        }

        // check the stochasticity

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage("BEAST 2.8", stochasticity, this.getCompatibleStochasticities())
            );
        }

        // the inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST 2.8 inputs)
        // we use reflection to get the expected inputs

        List<TileInput<?>> expectedInputs = this.getTileInputs();

        // for every specified TileInput, we collect the compatible tiles

        List<Set<Tile<?>>> compatibleInputTiles = new ArrayList<>();
        for (TileInput<?> tileInput : expectedInputs) {
            Set<Tile<?>> compatibleInputs = tileInput.getCompatibleInputTiles(node, allInputTiles, stochasticityResolver);

            if (compatibleInputs.isEmpty()) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        "BEAST 2.8 cannot deal with the value you provided for " + tileInput.getKey() + "."
                );
            }

            compatibleInputTiles.add(compatibleInputs);
        }

        // we now look at every combination of the inputs and create a freshly wired up tile

        return this.getWiredUpTiles(expectedInputs, compatibleInputTiles, node);
    }

    @Override
    public N getRootNode() {
        return (N) super.getRootNode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getClass().getSimpleName());
        for (Field field : getClass().getDeclaredFields()) {
            if (field.getType().equals(AstNodeTileInput.class)) {
                field.setAccessible(true);
                try {
                    AstNodeTileInput<?, ?> input = (AstNodeTileInput<?, ?>) field.get(this);
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

    public static class AstNodeTileInput<O, N extends AstNode> extends TileInput<O> {

        private final String key;
        private final Function<N, AstNode> getter;

        public AstNodeTileInput(String key, Function<N, AstNode> getter) {
            this(key, getter, EnumSet.allOf(Stochasticity.class));
        }

        public AstNodeTileInput(String key, Function<N, AstNode> getter, Set<Stochasticity> acceptedStochasticities) {
            super(true, acceptedStochasticities);
            this.key = key;
            this.getter = getter;
        }

        @Override
        public Set<Tile<?>> getCompatibleInputTiles(AstNode astNode, Map<AstNode, Set<Tile<?>>> inputTiles, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
            AstNode inputAstNode = this.getter.apply((N) astNode);
            return super.getCompatibleInputTiles(inputAstNode, inputTiles, stochasticityResolver);
        }

        @Override
        public String getKey() {
            return this.key;
        }

    }
}
