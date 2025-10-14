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
                "PositiveReal value ~ LogNormal(10 + 20)",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable("LogNormal"),
                                new Expr.Argument(new Expr.Binary(
                                        new Expr.Literal(10),
                                        new Token(TokenType.PLUS, "+", null, 1),
                                        new Expr.Literal(20)
                                ))
                        )
                )
        );

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
                "PositiveReal value ~ IID(LogNormal)()",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Call(
                                        new Expr.Variable("IID"),
                                        new Expr.Argument("LogNormal", new Expr.Variable("LogNormal"))
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
                                new Expr.Argument("meanLog", new Expr.Literal(10.5)),
                                new Expr.Argument("sdLog", new Expr.Variable("sdLog"))
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
                                new Expr.Argument("meanLog", new Expr.Literal(10.5)),
                                new Expr.Argument("sdLog", new Expr.Variable("sdLog"))
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
                                new Expr.Call(new Expr.Variable("abs"), new Expr.Argument(new Expr.Literal(5))),
                                new Expr.Call(new Expr.Variable("square"), new Expr.Argument(new Expr.Literal(2)))
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
                "PositiveReal value = (10.4\n"
                        + "+ 5.0)",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Grouping(
                                new Expr.Binary(
                                        new Expr.Literal(10.4),
                                        new Token(TokenType.PLUS, "+", null, 2),
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
                                new Expr.Argument(
                                        "a", new Expr.Literal(10.4)
                                ),
                                new Expr.Argument(
                                        "b", new Expr.Literal(5.0)
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
