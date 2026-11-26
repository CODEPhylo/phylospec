package tiling;

import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.InconsistentTilingException;
import org.phylospec.tiling.tiles.Tile;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reports child tiles that a tile keeps in its own fields instead of TileInput fields.
 *
 * <p>The tiling consistency check only follows TileInput fields. A tile that keeps its children
 * elsewhere, such as a list of logged variables, must report them in
 * {@link Tile#getWiredUpInputs()}; otherwise it can pick a different but equivalent tiling of a
 * shared draw, and the draw is built twice (as BEAST 3's ListVectorTile also notes).</p>
 */
public final class WiredInputs {

    private WiredInputs() {
    }

    /** The child tiles keyed by the AST node each one covers, as in Tile#getWiredUpInputs. */
    public static Map<AstNode, Tile<?, ?>> of(Collection<? extends Tile<?, ?>> tiles)
            throws InconsistentTilingException {
        Map<AstNode, Tile<?, ?>> inputs = new IdentityHashMap<>();

        for (Tile<?, ?> tile : tiles) {
            Tile<?, ?> previous = inputs.putIfAbsent(tile.getRootNode(), tile);

            if (previous != null && previous != tile) {
                throw new InconsistentTilingException(tile.getRootNode(), previous, tile);
            }
        }

        return inputs;
    }
}
