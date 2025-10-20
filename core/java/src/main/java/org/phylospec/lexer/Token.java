package org.phylospec.lexer;

import java.util.Objects;

public class Token {
    public final TokenType type;
    public final String lexeme;
    public final Object literal;

    public final int line;
    public final int start;
    public final int end;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.start = 0;
        this.end = 0;
    }

    public Token(TokenType type, String lexeme, Object literal, int line, int start, int end) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.start = start;
        this.end = end;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal  + " (line " + line + " " + start + ":" + end +  ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return line == token.line && start == token.start && end == token.end && type == token.type && Objects.equals(lexeme, token.lexeme) && Objects.equals(literal, token.literal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, lexeme, literal, line, start, end);
    }
}
