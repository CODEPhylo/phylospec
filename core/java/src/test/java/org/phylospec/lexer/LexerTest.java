package org.phylospec.lexer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class LexerTest {

    @Test
    public void testSingleCharacterTokens() {
        String source = "(),.-+/*=!~@==!=<>>=<=[]import";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.LEFT_PAREN, "(", null, 1), tokens.get(0));
        assertEquals(new Token(TokenType.RIGHT_PAREN, ")", null, 1), tokens.get(1));
        assertEquals(new Token(TokenType.COMMA, ",", null, 1), tokens.get(2));
        assertEquals(new Token(TokenType.DOT, ".", null, 1), tokens.get(3));
        assertEquals(new Token(TokenType.MINUS, "-", null, 1), tokens.get(4));
        assertEquals(new Token(TokenType.PLUS, "+", null, 1), tokens.get(5));
        assertEquals(new Token(TokenType.SLASH, "/", null, 1), tokens.get(6));
        assertEquals(new Token(TokenType.STAR, "*", null, 1), tokens.get(7));
        assertEquals(new Token(TokenType.EQUAL, "=", null, 1), tokens.get(8));
        assertEquals(new Token(TokenType.BANG, "!", null, 1), tokens.get(9));
        assertEquals(new Token(TokenType.TILDE, "~", null, 1), tokens.get(10));
        assertEquals(new Token(TokenType.AT, "@", null, 1), tokens.get(11));
        assertEquals(new Token(TokenType.EQUAL_EQUAL, "==", null, 1), tokens.get(12));
        assertEquals(new Token(TokenType.BANG_EQUAL, "!=", null, 1), tokens.get(13));
        assertEquals(new Token(TokenType.LESS, "<", null, 1), tokens.get(14));
        assertEquals(new Token(TokenType.GREATER, ">", null, 1), tokens.get(15));
        assertEquals(new Token(TokenType.GREATER_EQUAL, ">=", null, 1), tokens.get(16));
        assertEquals(new Token(TokenType.LESS_EQUAL, "<=", null, 1), tokens.get(17));
        assertEquals(new Token(TokenType.LEFT_SQUARE_BRACKET, "[", null, 1), tokens.get(18));
        assertEquals(new Token(TokenType.RIGHT_SQUARE_BRACKET, "]", null, 1), tokens.get(19));
        assertEquals(new Token(TokenType.IMPORT, "import", null, 1), tokens.get(20));
        assertEquals(new Token(TokenType.EOF, "", null, 1), tokens.get(21));

        assertEquals(tokens.size(), 22);
    }

    @Test
    public void testNumberLiterals() {
        String source = "10.5\n10234453\n+50\n-5";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.FLOAT, "10.5", 10.5, 1), tokens.get(0));
        assertEquals(new Token(TokenType.EOL, "\n", null, 1), tokens.get(1));
        assertEquals(new Token(TokenType.INT, "10234453", 10234453, 2), tokens.get(2));
        assertEquals(new Token(TokenType.EOL, "\n", null, 2), tokens.get(3));
        assertEquals(new Token(TokenType.PLUS, "+", null, 3), tokens.get(4));
        assertEquals(new Token(TokenType.INT, "50", 50, 3), tokens.get(5));
        assertEquals(new Token(TokenType.EOL, "\n", null, 3), tokens.get(6));
        assertEquals(new Token(TokenType.MINUS, "-", null, 4), tokens.get(7));
        assertEquals(new Token(TokenType.INT, "5", 5, 4), tokens.get(8));
        assertEquals(new Token(TokenType.EOF, "", null, 4), tokens.get(9));

        assertEquals(tokens.size(), 10);
    }

    @Test
    public void testStringLiterals() {
        String source = "\"Hallo this is a string\"\n\"This is a\nmultiline\r\nstring\"";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.STRING, "\"Hallo this is a string\"", "Hallo this is a string", 1), tokens.get(0));
        assertEquals(new Token(TokenType.EOL, "\n", null, 1), tokens.get(1));
        assertEquals(new Token(TokenType.STRING, "\"This is a\nmultiline\r\nstring\"", "This is a\nmultiline\r\nstring", 4), tokens.get(2));
        assertEquals(new Token(TokenType.EOF, "", null, 4), tokens.get(3));

        assertEquals(tokens.size(), 4);
    }

    @Test
    public void testKeywords() {
        String source = "true false";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        assertEquals(new Token(TokenType.TRUE, "true", null, 1), tokens.get(0));
        assertEquals(new Token(TokenType.FALSE, "false", null, 1), tokens.get(1));
        assertEquals(new Token(TokenType.EOF, "", null, 1), tokens.get(2));

        assertEquals(tokens.size(), 3);
    }

    @Test
    public void testMisc() {
        String source = "(),.-+/*=!~\ntrue\rfalse\r\n\"Hallo\"10\n\r\n10.5\nsomeFun()";

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        // 1st line: (),.-+/*!=~
        assertEquals(new Token(TokenType.LEFT_PAREN, "(", null, 1), tokens.get(0));
        assertEquals(new Token(TokenType.RIGHT_PAREN, ")", null, 1), tokens.get(1));
        assertEquals(new Token(TokenType.COMMA, ",", null, 1), tokens.get(2));
        assertEquals(new Token(TokenType.DOT, ".", null, 1), tokens.get(3));
        assertEquals(new Token(TokenType.MINUS, "-", null, 1), tokens.get(4));
        assertEquals(new Token(TokenType.PLUS, "+", null, 1), tokens.get(5));
        assertEquals(new Token(TokenType.SLASH, "/", null, 1), tokens.get(6));
        assertEquals(new Token(TokenType.STAR, "*", null, 1), tokens.get(7));
        assertEquals(new Token(TokenType.EQUAL, "=", null, 1), tokens.get(8));
        assertEquals(new Token(TokenType.BANG, "!", null, 1), tokens.get(9));
        assertEquals(new Token(TokenType.TILDE, "~", null, 1), tokens.get(10));
        assertEquals(new Token(TokenType.EOL, "\n", null, 1), tokens.get(11));

        // 2nd line: true
        assertEquals(new Token(TokenType.TRUE, "true", null, 2), tokens.get(12));
        assertEquals(new Token(TokenType.EOL, "\r", null, 2), tokens.get(13));

        // 3rd line: false
        assertEquals(new Token(TokenType.FALSE, "false", null, 3), tokens.get(14));
        assertEquals(new Token(TokenType.EOL, "\r\n", null, 3), tokens.get(15));

        // 4th line: "Hallo" 10
        assertEquals(new Token(TokenType.STRING, "\"Hallo\"", "Hallo", 4), tokens.get(16));
        assertEquals(new Token(TokenType.INT, "10", 10, 4), tokens.get(17));
        assertEquals(new Token(TokenType.EOL, "\n", null, 4), tokens.get(18));

        // 5th line: <empty>
        assertEquals(new Token(TokenType.EOL, "\r\n", null, 5), tokens.get(19));

        // 6th line: 10.5
        assertEquals(new Token(TokenType.FLOAT, "10.5", 10.5, 6), tokens.get(20));
        assertEquals(new Token(TokenType.EOL, "\n", null, 6), tokens.get(21));

        // 7th line: someFun()
        assertEquals(new Token(TokenType.IDENTIFIER, "someFun", null, 7), tokens.get(22));
        assertEquals(new Token(TokenType.LEFT_PAREN, "(", null, 7), tokens.get(23));
        assertEquals(new Token(TokenType.RIGHT_PAREN, ")", null, 7), tokens.get(24));

        // EOF
        assertEquals(new Token(TokenType.EOF, "", null, 7), tokens.get(25));

        assertEquals(tokens.size(), 26);
    }
}
