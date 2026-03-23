package org.phylospec.lexer;

import org.phylospec.Error;

public interface LexerEventListener {
    void lexerErrorDetected(Error error);
}
