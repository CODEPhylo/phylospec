package tiling;


import beastconfig.BEASTState;
import org.phylospec.ast.AstNode;
import org.phylospec.ast.Expr;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.typeresolver.StochasticityResolver;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class can be used to specify tile inputs.
 * @param <T> the type produced by the input tile.
 */
public abstract class TileInput<T> {
    private final boolean required;
    private final Set<Stochasticity> acceptedStochasticities;

    private TypeToken<T> typeToken;
    private Tile<T> tile;

    public TileInput(boolean required, Set<Stochasticity> acceptedStochasticities) {
        this.required = required;
        this.acceptedStochasticities = acceptedStochasticities;
    }

    /**
     * This can be called at runtime during reflection to resolve the type token
     * from the type parameter of the field.
     */
    void resolveTypeFromField(Field inputTileField) {
        if (this.typeToken != null) return;
        // TileInput<T> — T is the first type argument
        ParameterizedType fieldType = (ParameterizedType) inputTileField.getGenericType();
        this.typeToken = (TypeToken<T>) TypeToken.of(fieldType.getActualTypeArguments()[0]);
    }

    public void setTile(Tile<?> tile) {
        // we assume that the generated type is compatible
        try {
            this.tile = (Tile<T>) tile;
        } catch (ClassCastException e) {
            throw new RuntimeException("Incompatible tile assigned to a tile input. This should not happen.");
        }
    }

    /**
     * Returns the tiles rooted at 'inputAstNode' which have types compatible with this input.
     * Also checks that the stochasticity of 'inputAstNode' is accepted by this input.
     */
    public Set<Tile<?>> getCompatibleInputTiles(AstNode inputAstNode, Map<AstNode, Set<Tile<?>>> possibleInputTiles, StochasticityResolver stochasticityResolver) throws FailedTilingAttempt.RejectedCascade, FailedTilingAttempt.RejectedBoundary {
        // check the stochasticity of the input node
        Stochasticity stochasticity = stochasticityResolver.getStochasticity(inputAstNode);
        if (!this.acceptedStochasticities.contains(stochasticity)) {
            throw new FailedTilingAttempt.RejectedBoundary(
                    Stochasticity.getErrorMessage("BEAST 2.8", this.getKey(), stochasticity, this.acceptedStochasticities),
                    inputAstNode
            );
        }

        Set<Tile<?>> potentialInputs = possibleInputTiles.get(inputAstNode);

        if (potentialInputs == null || potentialInputs.isEmpty()) {
            throw new FailedTilingAttempt.RejectedCascade(inputAstNode);
        }

        TypeToken<?> expectedTypeToken = this.getTypeToken();

        Set<Tile<?>> compatibleInputs = new HashSet<>();
        for (Tile<?> potentialInput : potentialInputs) {
            if (expectedTypeToken.isAssignableFrom(potentialInput.getTypeToken())) {
                compatibleInputs.add(potentialInput);
            }
        }

        return compatibleInputs;
    }

    /**
     * Applies the input tile and its descendents to the given beast state.
     */
    public T apply(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return this.tile != null ? this.tile.apply(beastState, indexVariables) : null;
    }

    /* getter */

    public abstract String getKey();

    public Tile<T> getTile() {
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
