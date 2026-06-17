package org.phylospec.tiling.tiles;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;

/**
 * This class can be used to specify tile inputs.
 * @param <T> the type produced by the input tile.
 */
public abstract class TileInput<T, S> {
    private final boolean required;
    private final Set<Stochasticity> acceptedStochasticities;

    private TypeToken<T> typeToken;
    private Tile<T, S> tile;

    private Long requiredSize;
    private String requiredSizeMessage;

    public TileInput(boolean required, Set<Stochasticity> acceptedStochasticities) {
        this.required = required;
        this.acceptedStochasticities = acceptedStochasticities;
    }

    public TileInput<T, S> requireSize(long requiredSize, String message) {
        if (requiredSize < 0) {
            throw new IllegalArgumentException("Required size must be non-negative.");
        }

        this.requiredSize = requiredSize;
        this.requiredSizeMessage = message;
        return this;
    }

    public TileInput<T, S> requireSize(long requiredSize) {
        return requireSize(requiredSize, null);
    }

    /**
     * This can be called at runtime during reflection to resolve the type token
     * from the type parameter of the field.
     */
    void resolveTypeFromField(Field inputTileField) {
        if (this.typeToken != null) return;

        ParameterizedType fieldType = (ParameterizedType) inputTileField.getGenericType();
        this.typeToken = (TypeToken<T>) TypeToken.of(fieldType.getActualTypeArguments()[0]);
    }

    public void setTile(Tile<?, S> tile) {
        // we assume that the generated type is compatible
        try {
            this.tile = (Tile<T, S>) tile;
        } catch (ClassCastException e) {
            throw new RuntimeException(
                    "Incompatible tile assigned to a tile input. This should not happen.");
        }
    }

    /**
     * Returns the tiles rooted at 'inputAstNode' which have types compatible with this input.
     * Also checks that the stochasticity of 'inputAstNode' is accepted by this input.
     */
    public Set<Tile<?, S>> getCompatibleInputTiles(
            AstNode inputAstNode,
            Map<AstNode, Set<Tile<?, S>>> possibleInputTiles,
            StochasticityResolver stochasticityResolver)
            throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        // check the stochasticity of the input node
        Stochasticity stochasticity = stochasticityResolver.getStochasticity(inputAstNode);
        if (!this.acceptedStochasticities.contains(stochasticity)) {
            throw new FailedTilingAttempt.RejectedBoundary(
                    Stochasticity.getErrorMessage(
                            "BEAST 2.8",
                            this.getKey(),
                            stochasticity,
                            this.acceptedStochasticities));
        }

        Set<Tile<?, S>> potentialInputs = possibleInputTiles.get(inputAstNode);

        if (potentialInputs == null || potentialInputs.isEmpty()) {
            throw new FailedTilingAttempt.RejectedCascade(inputAstNode);
        }

        TypeToken<?> expectedTypeToken = this.getTypeToken();

        Set<Tile<?, S>> compatibleInputs = new HashSet<>();
        boolean sawTypeCompatibleInput = false;
        boolean sawWrongSizedInput = false;

        for (Tile<?, S> potentialInput : potentialInputs) {
            if (!expectedTypeToken.isAssignableFrom(potentialInput.getTypeToken())) {
                continue;
            }

            sawTypeCompatibleInput = true;

            if (!matchesRequiredSize(potentialInput)) {
                sawWrongSizedInput = true;
                continue;
            }

            compatibleInputs.add(potentialInput);
        }

        if (compatibleInputs.isEmpty() && sawTypeCompatibleInput && sawWrongSizedInput) {
            throw new FailedTilingAttempt.RejectedBoundary(getSizeErrorMessage());
        }

        return compatibleInputs;
    }

    private boolean matchesRequiredSize(Tile<?, S> potentialInput) {
        if (this.requiredSize == null) {
            return true;
        }

        OptionalLong actualSize = potentialInput.getFixedOutputSize();

        return actualSize.isPresent() && actualSize.getAsLong() == this.requiredSize;
    }

    private String getSizeErrorMessage() {
        if (this.requiredSizeMessage != null && !this.requiredSizeMessage.isBlank()) {
            return this.requiredSizeMessage;
        }

        return "BEAST 2.8 expects '" + this.getKey() + "' to have size " + this.requiredSize + ".";
    }

    /**
     * Applies the input tile and its descendents to the given state.
     */
    public T apply(S state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.tile != null ? this.tile.apply(state, indexVariables) : null;
    }

    /* getter */

    public abstract String getKey();

    public Tile<T, S> getTile() {
        return this.tile;
    }

    public boolean isRequired() {
        return this.required;
    }

    /**
     * Returns the type token produced by the input tile.
     */
    public TypeToken<?> getTypeToken() {
        return this.tile != null ? this.tile.getTypeToken() : this.typeToken;
    }
}
