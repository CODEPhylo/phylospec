package org.phylospec.lexer;

enum TokenType {
    // single-character tokens
    LEFT_PAREN, RIGHT_PAREN, COMMA, DOT, MINUS, PLUS,
    SLASH, STAR, BANG, EQUAL, TILDE, AT,

    // literals
    IDENTIFIER, STRING, INT, FLOAT,

    // keywords
    TRUE, FALSE, PRINT,

    // terminators
    EOL, EOF
}
