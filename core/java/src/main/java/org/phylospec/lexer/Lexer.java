package org.phylospec.lexer;

import org.phylospec.PhyloSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("true", TokenType.TRUE);
        keywords.put("false", TokenType.FALSE);
        keywords.put("print", TokenType.PRINT);
    }

    private final String source;

    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new Token(TokenType.EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            // single-character tokens
            case '(':
                addToken(TokenType.LEFT_PAREN);
                break;
            case ')':
                addToken(TokenType.RIGHT_PAREN);
                break;
            case ',':
                addToken(TokenType.COMMA);
                break;
            case '.':
                addToken(TokenType.DOT);
                break;
            case '-':
                addToken(TokenType.MINUS);
                break;
            case '+':
                addToken(TokenType.PLUS);
                break;
            case '*':
                addToken(TokenType.STAR);
                break;
            case '!':
                if (peek() == '=') {
                    advance();
                    addToken(TokenType.BANG_EQUAL);
                } else {
                    addToken(TokenType.BANG);
                }
                break;
            case '=':
                if (peek() == '=') {
                    advance();
                    addToken(TokenType.EQUAL_EQUAL);
                } else {
                    addToken(TokenType.EQUAL);
                }
                break;
            case '<':
                if (peek() == '=') {
                    advance();
                    addToken(TokenType.LESS_EQUAL);
                } else {
                    addToken(TokenType.LESS);
                }
                break;
            case '>':
                if (peek() == '=') {
                    advance();
                    addToken(TokenType.GREATER_EQUAL);
                } else {
                    addToken(TokenType.GREATER);
                }
                break;
            case '~':
                addToken(TokenType.TILDE);
                break;
            case '@':
                addToken(TokenType.AT);
                break;
            case '/': {
                if (match('/')) {
                    // this is a comment, it goes until the end of the line
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(TokenType.SLASH);
                }
                break;
            }

            // EOL tokens
            case '\n':
                addToken(TokenType.EOL);
                line++;
                break;
            case '\r':
                // we also consider "\r\n" as one new line, as it is done on Windows
                match('\n');
                addToken(TokenType.EOL);
                line++;
                break;

            // whitespace
            case ' ':
                break;
            case '\t':
                break;

            // string literals
            case '"':
                string();
                break;

            default:
                // handle all other types of tokens
                if (isAlpha(c)) {
                    identifier();
                } else if (isDigit(c)) {
                    number();
                }
        }
        ;
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n')
                line++;
            if (peek() == '\r') {
                // we also consider "\r\n" as one new line, as it is done on Windows
                if (peekNext() == '\n') {
                    advance();
                }
                line++;
            }
            advance();
        }

        if (isAtEnd()) {
            PhyloSpec.error(line, "Unterminated string.");
            return;
        }

        // The closing '"'.
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(start + 1, current - 1);
        addToken(TokenType.STRING, value);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);

        TokenType identifierType = keywords.get(text);
        if (identifierType == null) identifierType = TokenType.IDENTIFIER;

        addToken(identifierType);
    }

    private void number() {
        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            // this number has a fractional part, it is thus a float

            // Consume the "."
            advance();

            while (isDigit(peek())) advance();

            addToken(TokenType.FLOAT, Double.parseDouble(source.substring(start, current)));
        } else {
            // this number has no fractional part, it is thus an integer
            addToken(TokenType.INT, Integer.parseInt(source.substring(start, current)));
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private char advance() {
        return source.charAt(current++);
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (source.length() < current + 1) return '\0';
        return source.charAt(current + 1);
    }

    private boolean match(Character expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean isAtBeginning() {
        return current == 0;
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }
}
