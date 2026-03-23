package org.phylospec.lexer;

import org.junit.jupiter.api.Test;
import org.phylospec.FuzzingUtils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LexerTest {

    @Test
    public void testSingleCharacterTokens() {
        String source = "(),.-+/*=!~@==!=<>>=<=[]import for";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.LEFT_PAREN, "(", null, 1, 0, 1), tokens.get(0));
        assertEquals(new Token(TokenType.RIGHT_PAREN, ")", null, 1, 1, 2), tokens.get(1));
        assertEquals(new Token(TokenType.COMMA, ",", null, 1, 2, 3), tokens.get(2));
        assertEquals(new Token(TokenType.DOT, ".", null, 1, 3, 4), tokens.get(3));
        assertEquals(new Token(TokenType.MINUS, "-", null, 1, 4, 5), tokens.get(4));
        assertEquals(new Token(TokenType.PLUS, "+", null, 1, 5, 6), tokens.get(5));
        assertEquals(new Token(TokenType.SLASH, "/", null, 1, 6, 7), tokens.get(6));
        assertEquals(new Token(TokenType.STAR, "*", null, 1, 7, 8), tokens.get(7));
        assertEquals(new Token(TokenType.EQUAL, "=", null, 1, 8, 9), tokens.get(8));
        assertEquals(new Token(TokenType.BANG, "!", null, 1, 9, 10), tokens.get(9));
        assertEquals(new Token(TokenType.TILDE, "~", null, 1, 10, 11), tokens.get(10));
        assertEquals(new Token(TokenType.AT, "@", null, 1, 11, 12), tokens.get(11));
        assertEquals(new Token(TokenType.EQUAL_EQUAL, "==", null, 1, 12, 14), tokens.get(12));
        assertEquals(new Token(TokenType.BANG_EQUAL, "!=", null, 1, 14, 16), tokens.get(13));
        assertEquals(new Token(TokenType.LESS, "<", null, 1, 16, 17), tokens.get(14));
        assertEquals(new Token(TokenType.GREATER, ">", null, 1, 17, 18), tokens.get(15));
        assertEquals(new Token(TokenType.GREATER_EQUAL, ">=", null, 1, 18, 20), tokens.get(16));
        assertEquals(new Token(TokenType.LESS_EQUAL, "<=", null, 1, 20, 22), tokens.get(17));
        assertEquals(new Token(TokenType.LEFT_SQUARE_BRACKET, "[", null, 1, 22, 23), tokens.get(18));
        assertEquals(new Token(TokenType.RIGHT_SQUARE_BRACKET, "]", null, 1, 23, 24), tokens.get(19));
        assertEquals(new Token(TokenType.IMPORT, "import", null, 1, 24, 30), tokens.get(20));
        assertEquals(new Token(TokenType.FOR, "for", null, 1, 31, 34), tokens.get(21));
        assertEquals(new Token(TokenType.EOF, "", null, 1, 34, 34), tokens.get(22));

        assertEquals(tokens.size(), 23);
    }

    @Test
    public void testNumberLiterals() {
        String source = "10.5\n10234453\n+50\n-5";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.FLOAT, "10.5", 10.5, 1, 0, 4), tokens.get(0));
        assertEquals(new Token(TokenType.EOL, "\n", null, 1, 4, 5), tokens.get(1));
        assertEquals(new Token(TokenType.INT, "10234453", 10234453, 2, 0, 8), tokens.get(2));
        assertEquals(new Token(TokenType.EOL, "\n", null, 2, 8, 9), tokens.get(3));
        assertEquals(new Token(TokenType.PLUS, "+", null, 3, 0, 1), tokens.get(4));
        assertEquals(new Token(TokenType.INT, "50", 50, 3, 1, 3), tokens.get(5));
        assertEquals(new Token(TokenType.EOL, "\n", null, 3, 3, 4), tokens.get(6));
        assertEquals(new Token(TokenType.MINUS, "-", null, 4, 0, 1), tokens.get(7));
        assertEquals(new Token(TokenType.INT, "5", 5, 4, 1, 2), tokens.get(8));
        assertEquals(new Token(TokenType.EOF, "", null, 4, 2, 2), tokens.get(9));

        assertEquals(tokens.size(), 10);
    }

    @Test
    public void testStringLiterals() {
        String source = "\"Hallo this is a string\"\n\"This is a\nmultiline\r\nstring\"";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.STRING, "\"Hallo this is a string\"", "Hallo this is a string", 1, 0, 24), tokens.get(0));
        assertEquals(new Token(TokenType.EOL, "\n", null, 1, 24, 25), tokens.get(1));
        assertEquals(new Token(TokenType.STRING, "\"This is a\nmultiline\r\nstring\"", "This is a\nmultiline\r\nstring", new Range(2, 4, 0, 8)), tokens.get(2));
        assertEquals(new Token(TokenType.EOF, "", null, 4, 8, 8), tokens.get(3));

        assertEquals(tokens.size(), 4);
    }

    @Test
    public void testKeywords() {
        String source = "true false for";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.TRUE, "true", null, 1, 0, 4), tokens.get(0));
        assertEquals(new Token(TokenType.FALSE, "false", null, 1, 5, 10), tokens.get(1));
        assertEquals(new Token(TokenType.FOR, "for", null, 1, 11, 14), tokens.get(2));
        assertEquals(new Token(TokenType.EOF, "", null, 1, 14, 14), tokens.get(3));

        assertEquals(tokens.size(), 4);
    }

    @Test
    public void testMisc() {
        String source = "(),.-+/*=!~\ntrue\rfalse\r\n\"Hallo\"10\n\r\n10.5  // this is some comment\nsomeFun()";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        // 1st line: (),.-+/*!=~
        assertEquals(new Token(TokenType.LEFT_PAREN, "(", null, 1, 0, 1), tokens.get(0));
        assertEquals(new Token(TokenType.RIGHT_PAREN, ")", null, 1, 1, 2), tokens.get(1));
        assertEquals(new Token(TokenType.COMMA, ",", null, 1, 2, 3), tokens.get(2));
        assertEquals(new Token(TokenType.DOT, ".", null, 1, 3, 4), tokens.get(3));
        assertEquals(new Token(TokenType.MINUS, "-", null, 1, 4, 5), tokens.get(4));
        assertEquals(new Token(TokenType.PLUS, "+", null, 1, 5, 6), tokens.get(5));
        assertEquals(new Token(TokenType.SLASH, "/", null, 1, 6, 7), tokens.get(6));
        assertEquals(new Token(TokenType.STAR, "*", null, 1, 7, 8), tokens.get(7));
        assertEquals(new Token(TokenType.EQUAL, "=", null, 1, 8, 9), tokens.get(8));
        assertEquals(new Token(TokenType.BANG, "!", null, 1, 9, 10), tokens.get(9));
        assertEquals(new Token(TokenType.TILDE, "~", null, 1, 10, 11), tokens.get(10));
        assertEquals(new Token(TokenType.EOL, "\n", null, 1, 11, 12), tokens.get(11));

        // 2nd line: true
        assertEquals(new Token(TokenType.TRUE, "true", null, 2, 0, 4), tokens.get(12));
        assertEquals(new Token(TokenType.EOL, "\r", null, 2, 4, 5), tokens.get(13));

        // 3rd line: false
        assertEquals(new Token(TokenType.FALSE, "false", null, 3, 0, 5), tokens.get(14));
        assertEquals(new Token(TokenType.EOL, "\r\n", null, 3, 5, 7), tokens.get(15));

        // 4th line: "Hallo" 10
        assertEquals(new Token(TokenType.STRING, "\"Hallo\"", "Hallo", 4, 0, 7), tokens.get(16));
        assertEquals(new Token(TokenType.INT, "10", 10, 4, 7, 9), tokens.get(17));
        assertEquals(new Token(TokenType.EOL, "\n", null, 4, 9, 10), tokens.get(18));

        // 5th line: <empty>
        assertEquals(new Token(TokenType.EOL, "\r\n", null, 5, 0, 2), tokens.get(19));

        // 6th line: 10.5  // this is some comment
        assertEquals(new Token(TokenType.FLOAT, "10.5", 10.5, 6, 0, 4), tokens.get(20));
        assertEquals(new Token(TokenType.COMMENT, "// this is some comment", " this is some comment", 6, 6, 29), tokens.get(21));
        assertEquals(new Token(TokenType.EOL, "\n", null, 6, 29, 30), tokens.get(22));

        // 7th line: someFun()
        assertEquals(new Token(TokenType.IDENTIFIER, "someFun", null, 7, 0, 7), tokens.get(23));
        assertEquals(new Token(TokenType.LEFT_PAREN, "(", null, 7, 7, 8), tokens.get(24));
        assertEquals(new Token(TokenType.RIGHT_PAREN, ")", null, 7, 8, 9), tokens.get(25));

        // EOF
        assertEquals(new Token(TokenType.EOF, "", null, 7, 9, 9), tokens.get(26));

        assertEquals(tokens.size(), 27);
    }

    @Test
    public void testErrors() {
        String source = "()$Hallo 1324523564356345892013245235643563458920 -5643563458920.4523564356345892045235643563458920 \"khkh";

        List<org.phylospec.Error> errors = new ArrayList<>();
        LexerEventListener listener = errors::add;

        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(listener);
        lexer.scanTokens();

        assertEquals(3, errors.size());

        assertEquals("(line 1 2:3)", errors.get(0).range().toString());
        assertEquals("'$' is not an allowed character.", errors.get(0).description());
        assertEquals("Only use letters or digits.", errors.get(0).hint());

        assertEquals("(line 1 9:49)", errors.get(1).range().toString());
        assertEquals("'1324523564356345892013245235643563458920' is not a valid number.", errors.get(1).description());
        assertEquals("Try a smaller number.", errors.get(1).hint());

        assertEquals("(line 1 100:105)", errors.get(2).range().toString());
        assertEquals("A string must be terminated with an '\"'.", errors.get(2).description());
        assertEquals("Use quotation marks to end the string.", errors.get(2).hint());
    }

    @Test
    public void testFuzz() {
        Random random = new Random(0);

        for (int i = 0; i < 10000; i++) {
            String input = generateFuzzInput(random, i);
            List<Token> tokens;

            try {
                tokens = new Lexer(input).scanTokens();
            } catch (Exception e) {
                fail("Lexer threw an exception on iteration " + i
                        + " (input=" + repr(input) + "): " + e);
                return;
            }

            // invariant: result is never null or empty
            assertNotNull(tokens, "tokens must not be null (iter=" + i + ")");

            // invariant: last token is always EOF
            assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type,
                    "last token must be EOF (iter=" + i + ")");

            // invariant: all token ranges are internally consistent
            for (Token token : tokens) {
                assertTrue(token.range.startLine >= 1,
                        "startLine must be >= 1 (iter=" + i + ", token=" + token + ")");
                assertTrue(token.range.start >= 0,
                        "start must be >= 0 (iter=" + i + ", token=" + token + ")");
                assertTrue(token.range.endLine >= token.range.startLine,
                        "end must be >= start (iter=" + i + ", token=" + token + ")");

                if (token.range.startLine == token.range.endLine) {
                    assertTrue(token.range.end >= token.range.start,
                            "end must be >= start (iter=" + i + ", token=" + token + ")");
                }
            }
        }
    }

    // generates one fuzz input chosen from several strategies
    private String generateFuzzInput(Random r, int iteration) {
        // first few iterations cover deterministic edge cases
        switch (iteration) {
            case 0: return "";
            case 1: return " ";
            case 2: return "\n";
            case 3: return "\r\n";
            case 4: return "\"";
            case 5: return "\"unterminated";
            case 6: return "//";
            case 7: return "// comment only";
            case 8: return String.valueOf((char) 0);
            case 9: return "\t\t\t";
        }

        int strategy = r.nextInt(5);
        switch (strategy) {
            case 0:
                // random printable ASCII (32–126)
                return FuzzingUtils.randomString(r, r.nextInt(80) + 1, 32, 126);
            case 1:
                // full byte range including control characters
                return FuzzingUtils.randomString(r, r.nextInt(50) + 1, 0, 127);
            case 2:
                // digit-heavy input to exercise number parsing and overflow paths
                return FuzzingUtils.randomDigitHeavyString(r, r.nextInt(60) + 1);
            case 3:
                // mutated snippet of valid-looking PhyloSpec source
                return FuzzingUtils.mutate(r, FuzzingUtils.pickValidSnippet(r), r.nextInt(5) + 1);
            default:
                // very long random printable string
                return FuzzingUtils.randomString(r, r.nextInt(500) + 100, 32, 126);
        }
    }

    // returns a compact representation of a string for failure messages
    private String repr(String s) {
        if (s.length() > 60) return "\"" + s.substring(0, 60).replace("\n", "\\n") + "...\"";
        return "\"" + s.replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
