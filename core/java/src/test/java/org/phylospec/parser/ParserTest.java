package org.phylospec.parser;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserTest {

    @Test
    public void testSimpleMathematicalExpression() {
        testExpression(
                "10 + (-25.2 - 100 / (2 + 4))",
                new Expr.Binary(
                        new Expr.Literal(10),
                        new Token(TokenType.PLUS, "+", null, 1),
                        new Expr.Grouping(
                                new Expr.Binary(
                                        new Expr.Unary(
                                                new Token(TokenType.MINUS, "-", null, 1),
                                                new Expr.Literal(25.2)
                                        ),
                                        new Token(TokenType.MINUS, "-", null, 1),
                                        new Expr.Binary(
                                                new Expr.Literal(100),
                                                new Token(TokenType.SLASH, "/", null, 1),
                                                new Expr.Grouping(
                                                        new Expr.Binary(
                                                                new Expr.Literal(2),
                                                                new Token(TokenType.PLUS, "+", null, 1),
                                                                new Expr.Literal(4)
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    @Test
    public void testSimpleLogicalExpression() {
        testExpression(
                "true == !(10 >= 11)",
                new Expr.Binary(
                        new Expr.Literal(true),
                        new Token(TokenType.EQUAL_EQUAL, "==", null, 1),
                        new Expr.Unary(
                                new Token(TokenType.BANG, "!", null, 1),
                                new Expr.Grouping(
                                        new Expr.Binary(
                                                new Expr.Literal(10),
                                                new Token(TokenType.GREATER_EQUAL, ">=", null, 1),
                                                new Expr.Literal(11)
                                        )
                                )
                        )
                )
        );
    }

    void testExpression(String source, Expr expectedExpression) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        Expr actualExpression = parser.parse();

        assertEquals(expectedExpression, actualExpression);
    }
}
