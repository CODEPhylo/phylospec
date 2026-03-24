package org.phylospec.typeresolver;

import org.phylospec.ast.AstNode;
import org.phylospec.lexer.Range;

import java.util.List;

public class TypeError extends RuntimeException {
    private AstNode astNode;
    private final String description;
    private final String hint;
    private final List<String> examples;

    public TypeError(AstNode astNode, String description, String hint, List<String> examples) {
        super(description + (hint.isBlank() ? "" : " " + hint));
        this.astNode = astNode;
        this.description = description;
        this.hint = hint;
        this.examples = examples;
    }

    public TypeError(String description, String hint, List<String> examples) {
        this(null, description, hint, examples);
    }

    public TypeError(AstNode astNode, String description, String hint) {
        this(astNode, description, hint, List.of());
    }

    public TypeError(String description, String hint) {
        this(null, description, hint, List.of());
    }

    public TypeError(AstNode astNode, String description) {
        this(astNode, description, "", List.of());
    }

    public TypeError(String description) {
        this(null, description, "", List.of());
    }

    public TypeError attachAstNode(AstNode astNode) {
        this.astNode = astNode;
        return this;
    }

    public AstNode getAstNode() {
        return astNode;
    }

    public org.phylospec.errors.Error toError(Range range) {
        return new org.phylospec.errors.Error(range, description, hint, examples);
    }
}
