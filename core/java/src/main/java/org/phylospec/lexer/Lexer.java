package org.phylospec.lexer;

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
        this.source = source;
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

        String text = source.substring(start, current);

        TokenType identifierType = keywords.get(text);
        if (identifierType == null) {
            // handle the special case for the two-word identifiers "observed as" and "observed
            // between"
            if (text.equals("observed")) {
                if (peek(2).equals("as")) {
                    for (int i = 0; i <= 2; i++) advance();
                    identifierType = TokenType.OBSERVED_AS;
                } else if (peek(7).equals("between")) {
                    for (int i = 0; i <= 7; i++) advance();
                    identifierType = TokenType.OBSERVED_BETWEEN;
                } else {
                    identifierType = TokenType.IDENTIFIER;
                }
            } else {
                identifierType = TokenType.IDENTIFIER;
            }
        }

        addToken(identifierType);
    }

    private void number() {
        boolean isFloat = false;

        // we first try to scan the entire number

        // a number could be an int or float, contain digit separators ("10_000"),
        // or scientific notation ("10.3E-2")

        this.scanDigits();

        if (this.peek() == '.' && this.isDigit(this.peekNext())) {
            // this number has a fractional part, it is thus a float

            // consume the "."
            this.advance();
            this.scanDigits();
            isFloat = true;
        }

        if (this.isExponentStart()) {
            // this number has an exponent, it is thus a float

            // consume the "e" and the optional sign
            this.advance();
            if (this.peek() == '+' || this.peek() == '-') this.advance();
            this.scanDigits();
            isFloat = true;
        }

        String text = this.source.substring(this.start, this.current);

        if (!this.hasValidUnderscores(text)) {
            this.reportError("'" + text + "' is not a valid number.", "Underscores can only be placed between digits.");
            return;
        }

        // we now take "text" and actually parse it into a number

        String digits = text.replace("_", "");

        try {
            if (isFloat) {
                double value = Double.parseDouble(digits);
                if (Double.isInfinite(value)) throw new NumberFormatException();
                if (value == 0.0 && this.hasNonZeroMantissa(digits)) {
                    this.reportError("'" + text + "' is too close to zero to be represented.", "Try a larger number.");
                    return;
                }
                this.addToken(TokenType.FLOAT, value);
            } else {
                this.addToken(TokenType.INT, Integer.parseInt(digits));
            }
        } catch (NumberFormatException e) {
            this.reportError("'" + text + "' is not a valid number.", "Try a smaller number.");
        }
    }

    /**
     * Consumes a run of digits and underscores. Misplaced underscores are consumed as
     * well, such that they can be reported as part of the number.
     */
    private void scanDigits() {
        while (this.isDigit(this.peek()) || this.peek() == '_') this.advance();
    }

    /**
     * Checks if the cursor points to the start of an exponent, i.e. an 'e' or 'E',
     * followed by an optional sign and at least one digit. Underscores before the
     * first digit are allowed here, such that they can be reported as misplaced.
     */
    private boolean isExponentStart() {
        if (this.peek() != 'e' && this.peek() != 'E') return false;

        int offset = 1;
        if (this.peekAt(offset) == '+' || this.peekAt(offset) == '-') offset++;
        while (this.peekAt(offset) == '_') offset++;

        return this.isDigit(this.peekAt(offset));
    }

    /**
     * Checks that every underscore in the given number literal is placed between two digits.
     */
    private boolean hasValidUnderscores(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '_') continue;

            boolean hasDigitBefore = 0 < i && this.isDigit(text.charAt(i - 1));
            boolean hasDigitAfter = i + 1 < text.length() && this.isDigit(text.charAt(i + 1));
            if (!hasDigitBefore || !hasDigitAfter) return false;
        }

        return true;
    }

    /**
     * Checks if the part before the exponent of the given number literal contains
     * a non-zero digit, i.e. if the literal denotes a non-zero number.
     */
    private boolean hasNonZeroMantissa(String digits) {
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c == 'e' || c == 'E') return false;
            if ('1' <= c && c <= '9') return true;
        }

        return false;
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
     * Returns the character offset positions after the cursor without advancing the cursor.
     */
    private char peekAt(int offset) {
        if (this.source.length() <= this.current + offset) return '\0';
        return this.source.charAt(this.current + offset);
    }

    /**
     * Returns the next howMany current characters without advancing the cursor.
     */
    private String peek(int howMany) {
        return source.substring(current + 1, Math.min(current + howMany + 1, source.length()));
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
        Range range = new Range(startLine, currentLine, start - startLineStart, current - currentLineStart);
        tokens.add(new Token(type, text, literal, range));
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
