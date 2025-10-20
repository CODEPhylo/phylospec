package org.phylospec.parser;

import org.phylospec.lexer.Token;

public interface ParseEventListener {
    void parseErrorDetected(Token token, String message);
}
