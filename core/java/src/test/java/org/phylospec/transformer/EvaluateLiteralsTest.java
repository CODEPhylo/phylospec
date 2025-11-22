package org.phylospec.transformer;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiteralsTransformer;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EvaluateLiteralsTest {

    @Test
    public void testUnary() {
        testStatements(
                "Real a = -2",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(-2)
                )
        );
        testStatements(
                "Real a ~ Exponential(-2)",
                new Stmt.Draw(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Call(
                                "Exponential",
                                    new Expr.AssignedArgument(
                                            null, new Expr.Literal(-2)
                                    )
                        )
                )
        );
        testStatements(
                "Boolean a = !false",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(true)
                )
        );
    }
    @Test
    public void testBinary() {
        testStatements(
                "Real a = 10.0 + -2.0",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(8.0)
                )
        );
        testStatements(
                "Real a = 5*10.0 + -2*1 - 5",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(43.0)
                )
        );
        testStatements(
                "Real a = 1 + 12 / 4 - 10 * 2",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(-16)
                )
        );
        testStatements(
                "String a = \"A\" + \"B\" + \"C\"",
                new Stmt.Assignment(
                        new AstType.Atomic("String"), "a",
                        new Expr.Literal("ABC")
                )
        );
        testStatements(
                "Real a ~ Exponential(100 * 2)",
                new Stmt.Draw(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Call(
                                "Exponential",
                                new Expr.AssignedArgument(
                                        null, new Expr.Literal(200)
                                )
                        )
                )
        );
        testStatements(
                "Boolean a = true == !false",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(true)
                )
        );
        testStatements(
                "Boolean a = 10 + 2 == 12",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(true)
                )
        );
        testStatements(
                "Boolean a = 10 - 2 == 12",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(false)
                )
        );
        testStatements(
                "Boolean a = 10 - 2 < 14",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(true)
                )
        );
        testStatements(
                "Boolean a = 10 - 2 <= 14",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(true)
                )
        );
        testStatements(
                "Boolean a = 10 - 2 > 14",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(false)
                )
        );
        testStatements(
                "Boolean a = 10 - 2 >= 14",
                new Stmt.Assignment(
                        new AstType.Atomic("Boolean"), "a",
                        new Expr.Literal(false)
                )
        );
    }

    void testStatements(String source, Stmt... expectedStatements) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> actualStatements = parser.parse();

        EvaluateLiteralsTransformer transformer = new EvaluateLiteralsTransformer();
        actualStatements = transformer.transform(actualStatements);

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
