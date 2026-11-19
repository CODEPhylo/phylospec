package org.phylospec.lexer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;

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
        keywords.put("use", TokenType.IMPORT);
        keywords.put("for", TokenType.FOR);
        keywords.put("in", TokenType.IN);
    }

    private final String source;

    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int currentLine = 1;
    private int currentLineStart = 0;
    private int startLine = 1;
    private int startLineStart = 0;

    private final List<ErrorEventListener> eventListeners;

    /**
     * Creates a new Lexer capable of reading a PhyloSpec script and
     * splitting it up into tokens.
     *
     * @param source - the PhyloSpec script as a string.
     */
    public Lexer(String source) {
        // a leading byte order mark is not part of the script
        this.source = source.startsWith("\uFEFF") ? source.substring(1) : source;
        this.eventListeners = new ArrayList<>();
    }

    public void registerEventListener(ErrorEventListener listener) {
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
            scanToken();
        }

        start = current;
        startLine = currentLine;
        startLineStart = currentLineStart;

        addToken(TokenType.EOF);
        return tokens;
    }

    private void scanToken() {
        start = current;
        startLine = currentLine;
        startLineStart = currentLineStart;

        int c = advanceCodePoint();

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
            case '$':
                if (peek() == '$') {
                    advance();
                    addToken(TokenType.DOLLAR_DOLLAR);
                } else {
                    addToken(TokenType.DOLLAR);
                }
                break;
            case ':':
                addToken(TokenType.COLON);
                break;
            case '{':
                addToken(TokenType.LEFT_BRACE);
                break;
            case '}':
                addToken(TokenType.RIGHT_BRACE);
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
                if (isIdentifierStart(c)) {
                    identifier();
                } else if (isDigit(c)) {
                    number();
                } else if (c == ';' && peek() == '\n') {
                    reportError(
                            "';' is not allowed here.", "PhyloSpec does not use semicolons at the end of statements.");
                } else {
                    reportError(
                            "'" + new String(Character.toChars(c)) + "' is not an allowed character.",
                            "Use Unicode identifier characters or supported punctuation.");
                }
        }
    }

    private void string() {
        int currentPartStart = start + 1;

        while (peek() != '"' && !isAtEnd()) {
            // handle multiline strings

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

            // handle string interpolation

            if (previous() != '\\' && peek() == '$' && peekNext() == '{' && !isAtEnd()) {
                // we are in an interpolated part of the string

                // add a new string part

                String part = source.substring(currentPartStart, current);
                addToken(TokenType.STRING_PART, part);

                // consume the ${

                advance();
                advance();

                // lex the interpolated bit

                while (peek() != '}' && !isAtEnd()) {
                    scanToken();
                }

                if (isAtEnd()) {
                    reportError(
                            new Range(startLine, currentLine, start - startLineStart, current - currentLineStart),
                            "A string template must be terminated with an '}'.",
                            "Use curly brackets to add an variable name into a string.",
                            List.of("String name = \"file_{seed}.nex"));
                    return;
                }

                // consume the terminal }

                advance();

                start = current;
                startLine = currentLine;
                startLineStart = currentLineStart;
                currentPartStart = current;

            } else {
                // we are not in an interpolated area, simply advance the cursor
                advance();
            }
        }

        if (isAtEnd()) {
            reportError(
                    new Range(startLine, currentLine, start - startLineStart, current - currentLineStart),
                    "A string must be terminated with an '\"'.",
                    "Use quotation marks to end the string.");
            return;
        }

        // The closing '"'.
        advance();

        // Trim the surrounding quotes.
        String value = source.substring(currentPartStart, current - 1);
        addToken(TokenType.STRING_END, value);
    }

    private void identifier() {
        while (isIdentifierPart(peekCodePoint())) advanceCodePoint();

        // names are compared in NFC, so that differently encoded but canonically equivalent
        // spellings (e.g. a precomposed 'é' and 'e' followed by a combining accent) are the same name
        String text = Normalizer.normalize(this.source.substring(this.start, this.current), Normalizer.Form.NFC);

        TokenType identifierType = keywords.get(text);
        if (identifierType == null) {
            // handle the special case for the two-word identifiers "observed as" and "observed
            // between"
            if (text.equals("observed")) {
                if (this.isFollowedByWord("as")) {
                    for (int i = 0; i <= 2; i++) advance();
                    identifierType = TokenType.OBSERVED_AS;
                } else if (this.isFollowedByWord("between")) {
                    for (int i = 0; i <= 7; i++) advance();
                    identifierType = TokenType.OBSERVED_BETWEEN;
                } else {
                    identifierType = TokenType.IDENTIFIER;
                }
            } else {
                identifierType = TokenType.IDENTIFIER;
            }
        }

        if (identifierType == TokenType.IDENTIFIER) {
            // the range still covers the original spelling in the source
            this.addToken(identifierType, null, text);
        } else {
            addToken(identifierType);
        }
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
                        "'" + source.substring(start, current) + "' is not a valid number.", "Try a smaller number.");
            }
        } else {
            // this number has no fractional part, it is thus an integer
            try {
                addToken(TokenType.INT, Integer.parseInt(source.substring(start, current)));
            } catch (NumberFormatException e) {
                reportError(
                        "'" + source.substring(start, current) + "' is not a valid number.", "Try a smaller number.");
            }
        }
    }

    private void comment() {
        // this is a comment, it goes until the end of the line
        while (peek() != '\n' && !isAtEnd()) advance();
    }

    /* general helper methods */

    private boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private boolean isIdentifierStart(int codePoint) {
        return codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    private boolean isIdentifierPart(int codePoint) {
        return Character.isUnicodeIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint);
    }

    /* helper methods to inspect the source code */

    // Keep offsets in UTF-16 code units for source ranges and the language server,
    // but consume a whole Unicode code point when scanning identifiers.
    private int advanceCodePoint() {
        int codePoint = source.codePointAt(current);
        current += Character.charCount(codePoint);
        return codePoint;
    }

    private int peekCodePoint() {
        return isAtEnd() ? -1 : source.codePointAt(current);
    }

    /**
     * Returns the current character and advances the cursor afterward.
     */
    private char advance() {
        return source.charAt(current++);
    }

    /**
     * Returns the previous character without changing the cursor.
     */
    private char previous() {
        if (current == 0) return '\0';
        return source.charAt(current - 1);
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
     * Checks if the cursor is followed by a single space or tab and then the given word as a
     * complete identifier, without advancing the cursor.
     */
    private boolean isFollowedByWord(String word) {
        if (this.peek() != ' ' && this.peek() != '\t') return false;

        int wordStart = this.current + 1;
        int wordEnd = wordStart + word.length();
        if (!this.source.startsWith(word, wordStart)) return false;

        return wordEnd >= this.source.length() || !this.isIdentifierPart(this.source.codePointAt(wordEnd));
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
        this.addToken(type, literal, this.source.substring(this.start, this.current));
    }

    /**
     * Adds a new token with a corresponding literal and the given lexeme, which may differ from
     * the source text the token spans.
     */
    private void addToken(TokenType type, Object literal, String lexeme) {
        Range range = new Range(startLine, currentLine, start - startLineStart, current - currentLineStart);
        tokens.add(new Token(type, lexeme, literal, range));
    }

    /**
     * Reports an error to the registered event listeners.
     */
    private void reportError(Range range, String description, String hint) {
        for (ErrorEventListener eventListener : eventListeners) {
            eventListener.errorDetected(new Error(range, description, hint));
        }
    }

    /**
     * Reports an error to the registered event listeners.
     */
    private void reportError(Range range, String description, String hint, List<String> examples) {
        for (ErrorEventListener eventListener : eventListeners) {
            eventListener.errorDetected(new Error(range, description, hint, examples));
        }
    }

    /**
     * Reports an error to the registered event listeners.
     */
    private void reportError(String description, String hint) {
        Range range = new Range(currentLine, start - currentLineStart, current - currentLineStart);
        for (ErrorEventListener eventListener : eventListeners) {
            eventListener.errorDetected(new Error(range, description, hint));
        }
    }
}
