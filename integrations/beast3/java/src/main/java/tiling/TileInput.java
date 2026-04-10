package tiling;


import org.phylospec.ast.AstNode;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class can be used to specify tile inputs.
 * @param <T> the type produced by the input tile.
 */
public abstract class TileInput<T> {
    private final boolean required;

    private TypeToken<T> typeToken;
    private Tile<T> tile;

    public TileInput(boolean required) {
        this.required = required;
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
     */
    public Set<Tile<?>> getCompatibleInputTiles(AstNode inputAstNode, Map<AstNode, Set<Tile<?>>> possibleInputTiles) {
        Set<Tile<?>> potentialInputs = possibleInputTiles.get(inputAstNode);
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
    public T apply(BEASTState beastState) {
        return this.tile != null ? this.tile.apply(beastState) : null;
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
