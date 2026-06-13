package tiling;

import org.phylospec.ast.AstNode;

/**
 * Signals that a wired-up tile commits to two different sub-tiles for the same AstNode.
 * This happens when a tile has multiple TileInputs that feed from uses of the same variable
 * (e.g. {@code f(x, x)}) but {@code getWiredUpTiles} enumerated combinations where the two
 * slots picked different sub-tiles for the shared declaration.
 */
public class InconsistentTilingException extends Throwable {

    private final AstNode node;
    private final Tile<?> firstTile;
    private final Tile<?> secondTile;

    public InconsistentTilingException(AstNode node, Tile<?> firstTile, Tile<?> secondTile) {
        super("Two different sub-tiles were wired for the same AstNode.");
        this.node = node;
        this.firstTile = firstTile;
        this.secondTile = secondTile;
    }

    public AstNode getNode() {
        return this.node;
    }

    public Tile<?> getFirstTile() {
        return this.firstTile;
    }

    public Tile<?> getSecondTile() {
        return this.secondTile;
    }
}
