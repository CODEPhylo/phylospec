package org.phylospec.tiling.tiles;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Function;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.DimensionResolver;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;
import org.phylospec.typeresolver.VariableResolver;

/**
 * This class represents tiles that cover a single AstNode of type N. Extend this class for custom tiles.
 * Use AstNodeTileInput fields to specify the tile inputs (similar to BEAST 2.8 inputs).
 */
public abstract class AstNodeTile<T, N extends AstNode, S> extends Tile<T, S>
        implements CandidateTile<S> {

    public Class<N> getTargetNodeType() {
        return (Class<N>)
                ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
    }

    @Override
    public Set<Tile<?, S>> tryToTile(
            AstNode node,
            Map<AstNode, Set<Tile<?, S>>> allInputTiles,
            VariableResolver variableResolver,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt {
        if (!this.getTargetNodeType().isAssignableFrom(node.getClass())) {
            throw new FailedTilingAttempt.Irrelevant();
        }

        Stochasticity stochasticity = stochasticityResolver.getStochasticity(node);
        if (!this.getCompatibleStochasticities().contains(stochasticity)) {
            throw new FailedTilingAttempt.Rejected(
                    Stochasticity.getErrorMessage(
                            "BEAST 2.8", stochasticity, this.getCompatibleStochasticities()));
        }

        // the inputs correspond to the class fields with type GeneratorTile.Input (similar to BEAST
        // 2.8 inputs)
        // we use reflection to get the expected inputs
        List<TileInput<?, S>> expectedInputs = this.getTileInputs();

        DimensionResolver dimensionResolver = new DimensionResolver(variableResolver);

        List<Set<Tile<?, S>>> compatibleInputTiles = new ArrayList<>();
        for (TileInput<?, S> tileInput : expectedInputs) {
            Set<Tile<?, S>> compatibleInputs =
                    tileInput.getCompatibleInputTiles(
                            node, allInputTiles, stochasticityResolver, dimensionResolver);

            if (compatibleInputs.isEmpty()) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        "BEAST 2.8 cannot deal with the value you provided for "
                                + tileInput.getKey()
                                + ".");
            }

            compatibleInputTiles.add(compatibleInputs);
        }

        return this.getWiredUpTiles(expectedInputs, compatibleInputTiles, node, dimensionResolver);
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
                    AstNodeTileInput<?, ?, S> input = (AstNodeTileInput<?, ?, S>) field.get(this);
                    Tile<?, S> child = input.getTile();
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

    public static class AstNodeTileInput<O, N extends AstNode, S> extends TileInput<O, S> {

        private final String key;
        private final Function<N, AstNode> getter;

        public AstNodeTileInput(String key, Function<N, AstNode> getter) {
            this(key, getter, EnumSet.allOf(Stochasticity.class));
        }

        public AstNodeTileInput(
                String key,
                Function<N, AstNode> getter,
                Set<Stochasticity> acceptedStochasticities) {
            super(true, acceptedStochasticities);
            this.key = key;
            this.getter = getter;
        }

        @Override
        public Set<Tile<?, S>> getCompatibleInputTiles(
                AstNode astNode,
                Map<AstNode, Set<Tile<?, S>>> inputTiles,
                StochasticityResolver stochasticityResolver,
                DimensionResolver dimensionResolver)
                throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
            AstNode inputAstNode = this.getter.apply((N) astNode);
            return super.getCompatibleInputTiles(
                    inputAstNode, inputTiles, stochasticityResolver, dimensionResolver);
        }

        @Override
        public String getKey() {
            return this.key;
        }
    }
}
