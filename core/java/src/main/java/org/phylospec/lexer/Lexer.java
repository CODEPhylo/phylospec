package org.phylospec.lexer;

import org.phylospec.Error;
import org.phylospec.PhyloSpec;
import org.phylospec.parser.ParseEventListener;

import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class takes a PhyloSpec source code and splits it up
 * into tokens.
 */
public class Lexer {
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("true", TokenType.TRUE);
        keywords.put("false", TokenType.FALSE);
        keywords.put("import", TokenType.IMPORT);
        keywords.put("for", TokenType.FOR);
        keywords.put("in", TokenType.IN);
    }

    private final String source;

    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int currentLine = 1;
    private int currentLineStart = 0;

    private final List<LexerEventListener> eventListeners;

    /**
     * Creates a new Lexer capable of reading a PhyloSpec script and
     * splitting it up into tokens.
     *
     * @param source - the PhyloSpec script as a string.
     */
    public Lexer(String source) {
        this.source = source;
        this.eventListeners = new ArrayList<>();
    }

    public void registerEventListener (LexerEventListener listener) {
        this.eventListeners.add(listener);
    }

    /**
     * Reads the source code provided in the constructor and returns a list
     * of tokens.
     *
     * @return list of scanned tokens.
     */
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        start = current;
        addToken(TokenType.EOF);
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
            case '~':
                addToken(TokenType.TILDE);
                break;
            case '@':
                addToken(TokenType.AT);
                break;
            case '[':
                addToken(TokenType.LEFT_SQUARE_BRACKET);
                break;
            case ']':
                addToken(TokenType.RIGHT_SQUARE_BRACKET);
                break;

            // one or two character tokens
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
            case '/': {
                if (match('/')) {
                    comment();
                } else {
                    addToken(TokenType.SLASH);
                }
                break;
            }

            // EOL tokens
            case '\n':
                addToken(TokenType.EOL);
                currentLine++;
                currentLineStart = current;
                break;
            case '\r':
                // we also consider "\r\n" as one new line, as it is done on Windows
                match('\n');
                addToken(TokenType.EOL);
                currentLine++;
                currentLineStart = current;
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

            // all other types of tokens
            default:
                if (isAlpha(c)) {
                    identifier();
                } else if (isDigit(c)) {
                    number();
                } else {
                    reportError(
                            "'" + c + "' is not an allowed character.",
                            "Only use letters or digits."
                    );
                }
        }
    }

    private void string() {
        int startLine = currentLine;
        int startLineStart = currentLineStart;

        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                currentLine++;
                currentLineStart = current;
            }
            if (peek() == '\r') {
                // we also consider "\r\n" as one new line, as it is done on Windows
                if (peekNext() == '\n') {
                    advance();
                }
                currentLine++;
                currentLineStart = current;
            }
            advance();
        }

        if (isAtEnd()) {
            reportError(
                    new Range(startLine, currentLine, start - startLineStart, current - currentLineStart),
                    "A string must be terminated with an '\"'.",
                    "Use quotation marks to end the string."
            );
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

            try {
                addToken(TokenType.FLOAT, Double.parseDouble(source.substring(start, current)));
            } catch (NumberFormatException ignored) {
                reportError(
                        "'" + source.substring(start, current) + "' is not a valid number.",
                        "Try a smaller number."
                );
            }
        } else {
            // this number has no fractional part, it is thus an integer
            try {
                addToken(TokenType.INT, Integer.parseInt(source.substring(start, current)));
            } catch (NumberFormatException e) {
                reportError(
                        "'" + source.substring(start, current) + "' is not a valid number.",
                        "Try a smaller number."
                );
            }
        }
    }

    private void comment() {
        // this is a comment, it goes until the end of the line
        while (peek() != '\n' && !isAtEnd()) advance();
        addToken(TokenType.COMMENT, source.substring(start + 2, current));
    }

    /* general helper methods */

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

    /* helper methods to inspect the source code */

    /**
     * Returns the current character and advances the cursor afterward.
     */
    private char advance() {
        return source.charAt(current++);
    }

    /**
     * Returns the current character without advancing the cursor.
     */
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    /**
     * Returns the next current character without advancing the cursor.
     */
    private char peekNext() {
        if (source.length() <= current + 1) return '\0';
        return source.charAt(current + 1);
    }

    /**
     * Checks if the current character matches the expected one and
     * advances the cursor if that is the case.
     */
    private boolean match(Character expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    /**
     * Checks if the current cursor points to the end of the file.
     */
    private boolean isAtEnd() {
        return current >= source.length();
    }

    /* methods to add the found tokens */

    /**
     * Adds a new token with no corresponding literal.
     */
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    /**
     * Adds a new token with a corresponding literal.
     */
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, currentLine, start - currentLineStart, current - currentLineStart));
    }

    /**
     * Reports an error to the registered event listeners.
     */
    private void reportError(Range range, String description, String hint) {
        for (LexerEventListener eventListener : eventListeners) {
            eventListener.lexerErrorDetected(new Error(description, range, hint));
        }
    }

    /**
     * Reports an error to the registered event listeners.
     */
    private void reportError(String description, String hint) {
        Range range = new Range(currentLine, start - currentLineStart, current - currentLineStart);
        for (LexerEventListener eventListener : eventListeners) {
            eventListener.lexerErrorDetected(new Error(description, range, hint));
        }
    }
}
