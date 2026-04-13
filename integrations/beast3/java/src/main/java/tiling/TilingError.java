package tiling;

import org.phylospec.ast.AstNode;
import org.phylospec.errors.Error;
import org.phylospec.lexer.Range;

import java.util.List;

public class TilingError extends RuntimeException {
    private final AstNode node;
    private final String description;
    private final String hint;
    private final List<String> examples;

    public TilingError(String description, String hint) {
        this(null, description, hint);
    }

    public TilingError(AstNode node, String description, String hint) {
        this(node, description, hint, List.of());
    }

    public TilingError(AstNode node, String description, String hint, List<String> examples) {
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
}
