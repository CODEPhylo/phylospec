package org.phylospec.tiling.errors;

/**
 * Signals that a tiling which only keeps the lightest tiles rejected tiles whose pruned alternatives could have been
 * accepted. The outcome of such a tiling can differ from the outcome of the tiling with all tiles.
 */
public class PrunedTilingException extends RuntimeException {

    public PrunedTilingException() {
        super("Pruned tiles could have been needed for the tiling.");
    }
}
