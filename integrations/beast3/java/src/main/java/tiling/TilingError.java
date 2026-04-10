package tiling;

import org.phylospec.ast.AstNode;

public class TilingError extends RuntimeException {
    private final AstNode node;
    private final String description;
    private final String hint;

    public TilingError(String description, String hint) {
        this(null, description, hint);
    }

    public TilingError(AstNode node, String description, String hint) {
        super(description + " " + hint);
        this.node = node;
        this.description = description;
        this.hint = hint;
    }

}
