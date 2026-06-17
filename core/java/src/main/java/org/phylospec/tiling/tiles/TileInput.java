package org.phylospec.tiling.tiles;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Dimension;
import org.phylospec.tiling.DimensionUnifier;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.FailedTilingAttempt;
import org.phylospec.typeresolver.DimensionResolver;
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

    private Dimension requiredDimension;
    private String requiredDimensionMessage;

    public TileInput(boolean required, Set<Stochasticity> acceptedStochasticities) {
        this.required = required;
        this.acceptedStochasticities = acceptedStochasticities;
    }

    public TileInput<T, S> requireSize(long requiredSize, String message) {
        return requireDimension(Dimension.literal(requiredSize), message);
    }

    public TileInput<T, S> requireSize(long requiredSize) {
        return requireSize(requiredSize, null);
    }

    public TileInput<T, S> requireDimension(Dimension requiredDimension, String message) {
        if (requiredDimension == null) {
            throw new IllegalArgumentException("Required dimension must not be null.");
        }

        if (requiredDimension.isUnknown()) {
            throw new IllegalArgumentException("Required dimension must be known.");
        }

        this.requiredDimension = requiredDimension;
        this.requiredDimensionMessage = message;
        return this;
    }

    public TileInput<T, S> requireDimension(Dimension requiredDimension) {
        return requireDimension(requiredDimension, null);
    }

    public TileInput<T, S> requireDimensionVariable(String variableName, String message) {
        return requireDimension(Dimension.variable(variableName), message);
    }

    public TileInput<T, S> requireDimensionVariable(String variableName) {
        return requireDimensionVariable(variableName, null);
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
        try {
            this.tile = (Tile<T, S>) tile;
        } catch (ClassCastException e) {
            throw new RuntimeException(
                    "Incompatible tile assigned to a tile input. This should not happen.");
        }
    }

    /**
     * Returns the tiles rooted at 'inputAstNode' which have types compatible with this input.
     * Also checks that the stochasticity and dimension of 'inputAstNode' are accepted by this input.
     */
    public Set<Tile<?, S>> getCompatibleInputTiles(
            AstNode inputAstNode,
            Map<AstNode, Set<Tile<?, S>>> possibleInputTiles,
            StochasticityResolver stochasticityResolver,
            DimensionResolver dimensionResolver)
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
        Dimension mismatchedActualDimension = null;
        boolean sawUnknownDimensionInput = false;

        for (Tile<?, S> potentialInput : potentialInputs) {
            if (!expectedTypeToken.isAssignableFrom(potentialInput.getTypeToken())) {
                continue;
            }

            sawTypeCompatibleInput = true;

            DimensionUnifier probeUnifier = new DimensionUnifier();

            DimensionUnifier.Result dimensionResult =
                    unifyRequiredDimension(
                            potentialInput, inputAstNode, dimensionResolver, probeUnifier);

            if (dimensionResult == DimensionUnifier.Result.MISMATCH) {
                mismatchedActualDimension =
                        inferActualDimension(potentialInput, inputAstNode, dimensionResolver);
                continue;
            }

            if (dimensionResult == DimensionUnifier.Result.UNKNOWN) {
                sawUnknownDimensionInput = true;
                continue;
            }

            compatibleInputs.add(potentialInput);
        }

        if (compatibleInputs.isEmpty() && sawTypeCompatibleInput) {
            if (mismatchedActualDimension != null) {
                throw new FailedTilingAttempt.RejectedBoundary(
                        getDimensionMismatchErrorMessage(mismatchedActualDimension));
            }

            if (sawUnknownDimensionInput) {
                throw new FailedTilingAttempt.RejectedBoundary(getUnknownDimensionErrorMessage());
            }
        }

        return compatibleInputs;
    }

    /**
     * Applies this input's dimension requirement to an already selected input
     * tile. CandidateTile uses this method with one shared DimensionUnifier so
     * dimensions can be unified across multiple inputs of the same tile.
     */
    public DimensionUnifier.Result unifyRequiredDimension(
            Tile<?, S> inputTile,
            AstNode inputAstNode,
            DimensionResolver dimensionResolver,
            DimensionUnifier dimensionUnifier) {
        if (this.requiredDimension == null) {
            return DimensionUnifier.Result.MATCH;
        }

        Dimension actualDimension =
                inferActualDimension(inputTile, inputAstNode, dimensionResolver);

        return dimensionUnifier.unify(this.requiredDimension, actualDimension);
    }

    private Dimension inferActualDimension(
            Tile<?, S> inputTile, AstNode inputAstNode, DimensionResolver dimensionResolver) {
        Dimension outputDimension = inputTile.getOutputDimension();

        if (!outputDimension.isUnknown()) {
            return outputDimension;
        }

        if (dimensionResolver == null) {
            return Dimension.unknown();
        }

        return dimensionResolver.getDimension(inputAstNode);
    }

    private String getDimensionMismatchErrorMessage(Dimension actualDimension) {
        String baseMessage =
                this.requiredDimensionMessage != null && !this.requiredDimensionMessage.isBlank()
                        ? this.requiredDimensionMessage
                        : "The target backend expects '"
                                + this.getKey()
                                + "' to have dimension "
                                + this.requiredDimension.display()
                                + ".";

        return baseMessage + " Actual dimension: " + actualDimension.display() + ".";
    }

    private String getUnknownDimensionErrorMessage() {
        return "The target backend expects '"
                + this.getKey()
                + "' to have dimension "
                + this.requiredDimension.display()
                + ", but the input dimension could not be inferred during tiling.";
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
