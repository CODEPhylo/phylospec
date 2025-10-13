package org.phylospec.lexer;

enum TokenType {
    // single-character tokens
    LEFT_PAREN, RIGHT_PAREN, COMMA, DOT, MINUS, PLUS,
    SLASH, STAR, TILDE, AT,

    // one or two character tokens
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // literals
    IDENTIFIER, STRING, INT, FLOAT,

    // keywords
    TRUE, FALSE, PRINT,

    // terminators
    EOL, EOF
}
