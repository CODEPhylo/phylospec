package org.phylospec.transformer;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiteralsTransformer;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;
import org.phylospec.parser.Parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EvaluateLiteralsTest {

    @Test
    public void testUnary() {
        testStatements(
                "Real a = 10 + -2",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Binary(
                                new Expr.Literal(10),
                                TokenType.PLUS,
                                new Expr.Literal(-2)
                        )
                )
        );
        testStatements(
                "Boolean a = true == !false",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Binary(
                                new Expr.Literal(true),
                                TokenType.EQUAL_EQUAL,
                                new Expr.Literal(true)
                        )
                )
        );
    }
    @Test
    public void testBinary() {
        testStatements(
                "Real a = 10 + -2",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(8)
                )
        );
        testStatements(
                "Real a = 5*10 + -2*1 - 5",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(43)
                )
        );
        testStatements(
                "Real a = 1 + 12 / 4 - 10 * 2",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(-16)
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

        EvaluateLiteralsTransformer transformer = new EvaluateLiteralsTransformer();
        actualStatements = transformer.transformStatements(actualStatements);

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
