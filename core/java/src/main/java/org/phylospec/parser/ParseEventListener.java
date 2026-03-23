package org.phylospec.parser;

import org.phylospec.Error;
import org.phylospec.lexer.Token;

public interface ParseEventListener {
    void parseErrorDetected(Token token, Error error);
}
