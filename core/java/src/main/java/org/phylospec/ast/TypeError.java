package org.phylospec.ast;

import org.phylospec.lexer.TokenRange;

public class TypeError extends RuntimeException {
    AstNode astNode = null;

    public TypeError(String message) {
        super(message);
    }
    public TypeError(AstNode astNode, String message) {
        super(message);
        this.astNode = astNode;
    }

    public AstNode getAstNode() {
        return astNode;
    }
}
