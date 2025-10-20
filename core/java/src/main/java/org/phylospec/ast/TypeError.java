package org.phylospec.ast;

import org.phylospec.lexer.TokenRange;

public class TypeError extends RuntimeException {
    TokenRange tokenRange = null;

    public TypeError(String message) {
        super(message);
    }
    public TypeError(TokenRange tokenRange, String message) {
        super(message);
        this.tokenRange = tokenRange;
    }

    public TokenRange getTokenRange() {
        return tokenRange;
    }
}
