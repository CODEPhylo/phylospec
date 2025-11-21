package org.phylospec.transformer;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.RemoveGroupingsTransformer;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;
import org.phylospec.parser.Parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemoveGroupingTest {

    @Test
    public void testSingleGrouping() {
        testStatements(
                "Real a = (10 + 2)",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Binary(
                                new Expr.Literal(10),
                                TokenType.PLUS,
                                new Expr.Literal(2)
                        )
                )
        );
    }

    @Test
    public void testNestedGroupings() {
        testStatements(
                "Real a = (10 + (100 * 5 + 2))",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Binary(
                                new Expr.Literal(10),
                                TokenType.PLUS,
                                new Expr.Binary(
                                        new Expr.Binary(
                                                new Expr.Literal(100),
                                                TokenType.STAR,
                                                new Expr.Literal(5)
                                        ),
                                        TokenType.PLUS,
                                        new Expr.Literal(2)
                                )
                        )
                )
        );
    }

    @Test
    public void testGroupingsInFunctionCalls() {
        testStatements(
                "PositiveReal value ~ LogNormal((100 + 50))",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "LogNormal",
                                new Expr.AssignedArgument(new Expr.Binary(
                                        new Expr.Literal(100),
                                        TokenType.PLUS,
                                        new Expr.Literal(50)
                                ))
                        )
                )
        );
    }

    void testStatements(String source, Stmt... expectedStatements) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> actualStatements = parser.parse();

        RemoveGroupingsTransformer transformer = new RemoveGroupingsTransformer();
        actualStatements = transformer.transformStatements(actualStatements);

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
