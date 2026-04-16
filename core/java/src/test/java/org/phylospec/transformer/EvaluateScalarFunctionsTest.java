package org.phylospec.transformer;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EvaluateScalarFunctionsTest {

    @Test
    public void testLog() {
        testStatements(
                "Real a = log(10)",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(2.302585092994046)
                )
        );
        testStatements(
                "Real a ~ Exponential(log(10, base=10))",
                new Stmt.Draw(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Call(
                                "Exponential",
                                new Expr.AssignedArgument(
                                        null, new Expr.Literal(1.0)
                                )
                        )
                )
        );
    }

    @Test
    public void testExp() {
        testStatements(
                "Real a = exp(10)",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(22026.465794806718)
                )
        );
        testStatements(
                "Real a ~ Exponential(exp(0))",
                new Stmt.Draw(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Call(
                                "Exponential",
                                new Expr.AssignedArgument(
                                        null, new Expr.Literal(1.0)
                                )
                        )
                )
        );
    }

    @Test
    public void testSqrt() {
        testStatements(
                "Real a = sqrt(4)",
                new Stmt.Assignment(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Literal(2.0)
                )
        );
        testStatements(
                "Real a ~ Exponential(sqrt(0))",
                new Stmt.Draw(
                        new AstType.Atomic("Real"), "a",
                        new Expr.Call(
                                "Exponential",
                                new Expr.AssignedArgument(
                                        null, new Expr.Literal(0.0)
                                )
                        )
                )
        );
    }

    void testStatements(String source, Stmt... expectedStatements) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> actualStatements = parser.parse();

        EvaluateScalarFunctions transformer = new EvaluateScalarFunctions();
        actualStatements = transformer.transform(actualStatements);

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
