package org.phylospec.parser;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenRange;
import org.phylospec.lexer.TokenType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserTokenMappingTest {

    @Test
    public void testEmptyStatement() {
        testStatements("// this is a comment\n\n// another one");
    }

    @Test
    public void testMathematicalStatement() {
        testStatements(
                "Object var = 10 + (-25.2 - 100 / (2 + 4))",
                new Stmt.Assignment(
                        new AstType.Atomic("Object"), "var",
                        new Expr.Binary(
                                new Expr.Literal(10),
                                TokenType.PLUS,
                                new Expr.Grouping(
                                        new Expr.Binary(
                                                new Expr.Unary(
                                                        TokenType.MINUS,
                                                        new Expr.Literal(25.2)
                                                ),
                                                TokenType.MINUS,
                                                new Expr.Binary(
                                                        new Expr.Literal(100),
                                                        TokenType.SLASH,
                                                        new Expr.Grouping(
                                                                new Expr.Binary(
                                                                        new Expr.Literal(2),
                                                                        TokenType.PLUS,
                                                                        new Expr.Literal(4)
                                                                )
                                                        )
                                                )
                                        )
                                )
                        ),
                        new TokenRange(0, 0, 0)
                )
        );
    }


    void testStatements(String source, Stmt... expectedStatements) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> actualStatements = parser.parse();

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
