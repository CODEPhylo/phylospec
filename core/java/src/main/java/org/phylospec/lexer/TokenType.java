package org.phylospec.lexer;

public enum TokenType {
    // single-character tokens
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, COMMA, DOT, MINUS, PLUS,
    SLASH, STAR, TILDE, AT, LEFT_SQUARE_BRACKET, RIGHT_SQUARE_BRACKET,
    DOLLAR, COLON,

    // one or two character tokens
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // literals
    IDENTIFIER, INT, FLOAT,

    // strings
    STRING_PART, STRING_END,

    // keywords
    TRUE, FALSE, IMPORT, FOR, IN, OBSERVED_AS, OBSERVED_BETWEEN,

    // terminators
    EOL, EOF,

    // comment
    COMMENT;

    public static String getLexeme(TokenType token) {
        return switch (token) {
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case BANG -> "!";
            case GREATER -> ">";
            case GREATER_EQUAL -> ">=";
            case LESS -> "<";
            case LESS_EQUAL -> "<=";
            case EQUAL_EQUAL -> "==";
            case BANG_EQUAL -> "!=";
            default -> "";
        };
    }
}
