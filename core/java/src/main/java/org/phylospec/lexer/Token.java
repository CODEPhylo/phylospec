package org.phylospec.lexer;

import java.util.Objects;

public class Token {
    public final TokenType type;
    public final String lexeme;
    public final Object literal;

    public final TokenRange range;

    public Token(TokenType type, String lexeme, Object literal, int line, int start, int end) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.range = new TokenRange(line, start, end);
    }

    public String toString() {
        return type + " " + lexeme + " " + literal  + range.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return range.equals(token.range) && type == token.type && Objects.equals(lexeme, token.lexeme) && Objects.equals(literal, token.literal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, literal, range);
    }
}
