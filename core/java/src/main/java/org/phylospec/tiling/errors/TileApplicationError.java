package org.phylospec.tiling.errors;

import org.phylospec.ast.AstNode;
import org.phylospec.errors.Error;
import org.phylospec.lexer.Range;

import java.util.List;

/**
 * This class describes an error which occurs during an application of a tile.
 */
public class TileApplicationError extends RuntimeException {
    private AstNode node;
    private final String description;
    private final String hint;
    private final List<String> examples;

    public TileApplicationError(String description, String hint) {
        this(null, description, hint);
    }

    public TileApplicationError(AstNode node, String description, String hint) {
        this(node, description, hint, List.of());
    }

    public TileApplicationError(AstNode node, String description, String hint, List<String> examples) {
        super(description + " " + hint);
        this.node = node;
        this.description = description;
        this.hint = hint;
        this.examples = examples;
    }

    public AstNode getAstNode() {
        return node;
    }

    public Error toError(Range range) {
        return new Error(range, this.description, this.hint, this.examples);
    }

    public void setAstNode(AstNode node) {
        this.node = node;
    }
}
