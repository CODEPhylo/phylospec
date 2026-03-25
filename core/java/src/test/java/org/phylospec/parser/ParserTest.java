package org.phylospec.parser;

import org.junit.jupiter.api.Test;
import org.phylospec.FuzzingUtils;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.AstType;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class ParserTest {

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
                        )
                )
        );
    }

    @Test
    public void testLogicalStatement() {
        testStatements(
                "Object var = true == !(10 >= 11)",
                new Stmt.Assignment(
                        new AstType.Atomic("Object"), "var",
                        new Expr.Binary(
                                new Expr.Literal(true),
                                TokenType.EQUAL_EQUAL,
                                new Expr.Unary(
                                        TokenType.BANG,
                                        new Expr.Grouping(
                                                new Expr.Binary(
                                                        new Expr.Literal(10),
                                                        TokenType.GREATER_EQUAL,
                                                        new Expr.Literal(11)
                                                )
                                        )
                                )
                        )
                )
        );
    }

    @Test
    public void testTypes() {
        testStatements(
                "PositiveReal value = 10.4",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal value ~ 10.4",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal<T> value = 10.4",
                new Stmt.Assignment(
                        new AstType.Generic("PositiveReal", new AstType.Atomic("T")),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal<T<M>> value ~ 10.4",
                new Stmt.Draw(
                        new AstType.Generic("PositiveReal", new AstType.Generic("T", new AstType.Atomic("M"))),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal<T<M>, B<B,D>> value ~ 10.4",
                new Stmt.Draw(
                        new AstType.Generic("PositiveReal",
                                new AstType.Generic("T", new AstType.Atomic("M")),
                                new AstType.Generic("B", new AstType.Atomic("B"), new AstType.Atomic("D"))
                        ),
                        "value",
                        new Expr.Literal(10.4)
                )
        );
    }

    @Test
    public void testFunctionCalls() {
        testStatements(
                "PositiveReal value ~ LogNormal()",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "LogNormal"
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(10 + 20)",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "LogNormal",
                                new Expr.AssignedArgument(new Expr.Binary(
                                        new Expr.Literal(10),
                                        TokenType.PLUS,
                                        new Expr.Literal(20)
                                ))
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(meanLog = 10.5, sdLog)",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "LogNormal",
                                new Expr.AssignedArgument("meanLog", new Expr.Literal(10.5)),
                                new Expr.AssignedArgument(null, new Expr.Variable("sdLog"))
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(meanLog = 10.5, sdLog,)",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "LogNormal",
                                new Expr.AssignedArgument("meanLog", new Expr.Literal(10.5)),
                                new Expr.AssignedArgument(null, new Expr.Variable("sdLog"))
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(meanLog ~ Exp(), sdLog ~ Normal())",
                new Stmt.Draw(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "LogNormal",
                                new Expr.DrawnArgument("meanLog", new Expr.Call("Exp")),
                                new Expr.DrawnArgument("sdLog", new Expr.Call("Normal"))
                        )
                )
        );
    }

    @Test
    public void testArrays() {
        testStatements(
                "PositiveReal value = []",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of())
                )
        );

        testStatements(
                "PositiveReal value = [10, 5, 200]",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of(
                                new Expr.Literal(10),
                                new Expr.Literal(5),
                                new Expr.Literal(200)
                        ))
                )
        );

        testStatements(
                "PositiveReal value = [10, 5, 200,]",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of(
                                new Expr.Literal(10),
                                new Expr.Literal(5),
                                new Expr.Literal(200)
                        ))
                )
        );

        testStatements(
                "PositiveReal value = [abs(5), square(2),]",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of(
                                new Expr.Call("abs", new Expr.AssignedArgument(new Expr.Literal(5))),
                                new Expr.Call("square", new Expr.AssignedArgument(new Expr.Literal(2)))
                        ))
                )
        );
    }

    @Test
    public void testMultipleLines() {
        testStatements(
                "PositiveReal value = 10.4\n"
                        + "PositiveReal value = 2.0",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                ),
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(2.0)
                )
        );

        testStatements(
                "PositiveReal value = 10.4\n\n"
                        + "PositiveReal value = 2.0",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                ),
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(2.0)
                )
        );

        testStatements(
                "PositiveReal value = (10.4\n\n"
                        + "+ 5.0)",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Grouping(
                                new Expr.Binary(
                                        new Expr.Literal(10.4),
                                        TokenType.PLUS,
                                        new Expr.Literal(5.0)
                                )
                        )
                )
        );

        testStatements(
                "PositiveReal value = func(\n"
                        + "a=10.4,\n"
                        + "b=5.0,\n"
                        + ")",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                "func",
                                new Expr.AssignedArgument(
                                        "a", new Expr.Literal(10.4)
                                ),
                                new Expr.AssignedArgument(
                                        "b", new Expr.Literal(5.0)
                                )
                        )
                )
        );

        testStatements(
                "PositiveReal value = [10, \n5, \n200]",
                new Stmt.Assignment(
                        new AstType.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of(
                                new Expr.Literal(10),
                                new Expr.Literal(5),
                                new Expr.Literal(200)
                        ))
                )
        );
    }

    @Test
    public void testDecorators() {
        testStatements(
                "@Decorator() PositiveReal value ~ 10",
                new Stmt.Decorated(
                        new Expr.Call("Decorator"),
                        new Stmt.Draw(
                                new AstType.Atomic("PositiveReal"),
                                "value",
                                new Expr.Literal(10)
                        )
                )
        );

        testStatements(
                "@Decorator1()\n@Decorator2()\nPositiveReal value ~ 10",
                new Stmt.Decorated(
                        new Expr.Call("Decorator1"),
                        new Stmt.Decorated(
                                new Expr.Call("Decorator2"),
                                new Stmt.Draw(
                                        new AstType.Atomic("PositiveReal"),
                                        "value",
                                        new Expr.Literal(10)
                                )
                        )
                )
        );
    }


    @Test
    public void testFuzz() {
        Random random = new Random(0);

        for (int i = 0; i < 10000; i++) {
            String input = generateFuzzInput(random, i);
            List<Stmt> statements;

            try {
                List<Token> tokens = new Lexer(input).scanTokens();
                statements = new Parser(tokens).parse();
            } catch (Exception e) {
                fail("Parser threw an exception on iteration " + i
                        + " (input=" + repr(input) + "): " + e);
                return;
            }

            // invariant: result and every element are non-null
            assertNotNull(statements, "statements must not be null (iter=" + i + ")");
            for (Stmt stmt : statements) {
                assertNotNull(stmt, "statement must not be null (iter=" + i + ")");
            }
        }
    }

    // generates one fuzz input chosen from several strategies
    private String generateFuzzInput(Random r, int iteration) {
        // first few iterations cover deterministic edge cases
        switch (iteration) {
            case 0: return "";
            case 1: return "\n";
            case 2: return "// comment only";
            case 3: return "@";
            case 4: return "=";
            case 5: return "Real x =";
            case 6: return "Real x = (";
            case 7: return "Real x = [";
            case 8: return "Real<> x = 1";
            case 9: return "import";
        }

        int strategy = r.nextInt(5);
        switch (strategy) {
            case 0:
                // random printable ASCII — stresses error recovery
                return FuzzingUtils.randomString(r, r.nextInt(80) + 1, 32, 126);
            case 1:
                // digit-heavy — stresses number parsing interactions with the parser
                return FuzzingUtils.randomDigitHeavyString(r, r.nextInt(60) + 1);
            case 2:
                // mutated valid PhyloSpec snippets — stresses near-valid paths
                return FuzzingUtils.mutate(r, pickValidStatement(r), r.nextInt(5) + 1);
            case 3:
                // multiple lines of mutated statements — stresses error recovery across lines
                return FuzzingUtils.mutate(r, pickValidStatement(r), r.nextInt(3))
                        + "\n"
                        + FuzzingUtils.mutate(r, pickValidStatement(r), r.nextInt(3));
            default:
                // full byte range — stresses the lexer+parser pipeline together
                return FuzzingUtils.randomString(r, r.nextInt(50) + 1, 0, 127);
        }
    }

    // valid PhyloSpec statements that cover the main grammar rules
    private String pickValidStatement(Random r) {
        String[] snippets = {
            "Real x = 1.0",
            "Real x ~ LogNormal(meanLog = 0.0, sdLog = 1.0)",
            "Real x = a + b * c",
            "Real x = [1, 2, 3]",
            "Real x = [v for v in values]",
            "import phylospec.distributions",
            "@Observed() Real x ~ Normal(mu = 0.0, sigma = 1.0)",
            "Real<T> x = func(a = 1, b = 2)",
            "Real x = (a + b) * (c - d)",
            "Real x = obj.field",
            "Real x = f(a = 1,)",
            "Real x = [1, 2,]",
        };
        return snippets[r.nextInt(snippets.length)];
    }

    // returns a compact representation of a string for failure messages
    private String repr(String s) {
        if (s.length() > 60) return "\"" + s.substring(0, 60).replace("\n", "\\n") + "...\"";
        return "\"" + s.replace("\n", "\\n").replace("\r", "\\r") + "\"";
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
