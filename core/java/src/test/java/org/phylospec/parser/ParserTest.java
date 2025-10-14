package org.phylospec.parser;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.Type;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.lexer.TokenType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                        new Type.Atomic("Object"), "var",
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
                )
        );
    }

    @Test
    public void testLogicalStatement() {
        testStatements(
                "Object var = true == !(10 >= 11)",
                new Stmt.Assignment(
                        new Type.Atomic("Object"), "var",
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
                )
        );
    }

    @Test
    public void testTypes() {
        testStatements(
                "PositiveReal value = 10.4",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal value ~ 10.4",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal<T> value = 10.4",
                new Stmt.Assignment(
                        new Type.Generic("PositiveReal", new Type.Atomic("T")),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal<T<M>> value ~ 10.4",
                new Stmt.Draw(
                        new Type.Generic("PositiveReal", new Type.Generic("T", new Type.Atomic("M"))),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatements(
                "PositiveReal<T<M>, B<B,D>> value ~ 10.4",
                new Stmt.Draw(
                        new Type.Generic("PositiveReal",
                                new Type.Generic("T", new Type.Atomic("M")),
                                new Type.Generic("B", new Type.Atomic("B"), new Type.Atomic("D"))
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
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("LogNormal")
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(10 + 20)",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("LogNormal"),
                                new Expr.AssignedArgument(new Expr.Binary(
                                        new Expr.Literal(10),
                                        new Token(TokenType.PLUS, "+", null, 1),
                                        new Expr.Literal(20)
                                ))
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ IID(LogNormal)()",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Call(
                                        new Expr.Variable("IID"),
                                        new Expr.AssignedArgument("LogNormal", new Expr.Variable("LogNormal"))
                                )
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(meanLog = 10.5, sdLog)",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("LogNormal"),
                                new Expr.AssignedArgument("meanLog", new Expr.Literal(10.5)),
                                new Expr.AssignedArgument("sdLog", new Expr.Variable("sdLog"))
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(meanLog = 10.5, sdLog,)",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("LogNormal"),
                                new Expr.AssignedArgument("meanLog", new Expr.Literal(10.5)),
                                new Expr.AssignedArgument("sdLog", new Expr.Variable("sdLog"))
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ LogNormal(meanLog ~ Exp(), sdLog ~ Normal())",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("LogNormal"),
                                new Expr.DrawnArgument("meanLog", new Expr.Call(new Expr.Variable("Exp"))),
                                new Expr.DrawnArgument("sdLog", new Expr.Call(new Expr.Variable(("Normal"))))
                        )
                )
        );
    }

    @Test
    public void testArrays() {
        testStatements(
                "PositiveReal value = []",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of())
                )
        );

        testStatements(
                "PositiveReal value = [10, 5, 200]",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
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
                        new Type.Atomic("PositiveReal"),
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
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Array(List.of(
                                new Expr.Call(new Expr.Variable("abs"), new Expr.AssignedArgument(new Expr.Literal(5))),
                                new Expr.Call(new Expr.Variable("square"), new Expr.AssignedArgument(new Expr.Literal(2)))
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
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                ),
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(2.0)
                )
        );

        testStatements(
                "PositiveReal value = 10.4\n\n"
                        + "PositiveReal value = 2.0",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                ),
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(2.0)
                )
        );

        testStatements(
                "PositiveReal value = (10.4\n\n"
                        + "+ 5.0)",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Grouping(
                                new Expr.Binary(
                                        new Expr.Literal(10.4),
                                        new Token(TokenType.PLUS, "+", null, 3),
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
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("func"),
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
                        new Type.Atomic("PositiveReal"),
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
    public void testPropertyAccess() {
        testStatements(
                "PositiveReal value ~ constants.pi",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Get(
                                new Expr.Variable("constants"),
                                "pi"
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ constants.pi.binary",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Get(
                                new Expr.Get(
                                        new Expr.Variable("constants"),
                                        "pi"
                                ),
                                "binary"
                        )
                )
        );
    }

    @Test
    public void testMethodCalls() {
        testStatements(
                "PositiveReal value ~ constants.pi()",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Get(
                                        new Expr.Variable("constants"),
                                        "pi"
                                )
                        )
                )
        );

        testStatements(
                "PositiveReal value ~ constants.pi().binary.toReal()",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Get(
                                        new Expr.Get(
                                                new Expr.Call(
                                                        new Expr.Get(
                                                                new Expr.Variable("constants"),
                                                                "pi"
                                                        )
                                                ), "binary"
                                        ), "toReal"
                                )
                        )
                )
        );
    }

    @Test
    public void testDecorators() {
        testStatements(
                "@Decorator() PositiveReal value ~ 10",
                new Stmt.Decorated(
                        new Expr.Call(new Expr.Variable("Decorator")),
                        new Stmt.Draw(
                            new Type.Atomic("PositiveReal"),
                            "value",
                            new  Expr.Literal(10)
                        )
                )
        );

        testStatements(
                "@Decorator1()\n@Decorator2()\nPositiveReal value ~ 10",
                new Stmt.Decorated(
                        new Expr.Call(new Expr.Variable("Decorator1")),
                        new Stmt.Decorated(
                                new Expr.Call(new Expr.Variable("Decorator2")),
                                new Stmt.Draw(
                                        new Type.Atomic("PositiveReal"),
                                        "value",
                                        new  Expr.Literal(10)
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

        assertEquals(expectedStatements.length, actualStatements.size());

        for (int i = 0; i < expectedStatements.length; i++) {
            assertEquals(expectedStatements[i], actualStatements.get(i));
        }
    }
}
