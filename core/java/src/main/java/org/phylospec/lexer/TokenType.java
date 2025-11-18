package org.phylospec.lexer;

public enum TokenType {
    // single-character tokens
    LEFT_PAREN, RIGHT_PAREN, COMMA, DOT, MINUS, PLUS,
    SLASH, STAR, TILDE, AT, LEFT_SQUARE_BRACKET, RIGHT_SQUARE_BRACKET, FOR,

    // one or two character tokens
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // literals
    IDENTIFIER, STRING, INT, FLOAT,

    // keywords
    TRUE, FALSE, IMPORT,

    // terminators
    EOL, EOF;

    public static String getLexeme(TokenType token) {
        return switch (token) {
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case BANG -> "!";
            default -> "";
        };
    }
}
