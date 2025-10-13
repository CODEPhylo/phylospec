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
        testStatement(
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
        testStatement(
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
        testStatement(
                "PositiveReal value = 10.4",
                new Stmt.Assignment(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatement(
                "PositiveReal value ~ 10.4",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatement(
                "PositiveReal<T> value = 10.4",
                new Stmt.Assignment(
                        new Type.Generic("PositiveReal", new Type.Atomic("T")),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatement(
                "PositiveReal<T<M>> value ~ 10.4",
                new Stmt.Draw(
                        new Type.Generic("PositiveReal", new Type.Generic("T", new Type.Atomic("M"))),
                        "value",
                        new Expr.Literal(10.4)
                )
        );

        testStatement(
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
        testStatement(
                "PositiveReal value ~ LogNormal()",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable(new Token(TokenType.IDENTIFIER, "LogNormal", null, 1))
                        )
                )
        );

        testStatement(
                "PositiveReal value ~ IID(LogNormal)()",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Call(
                                    new Expr.Variable(new Token(TokenType.IDENTIFIER, "IID", null, 1)),
                                    new Expr.Argument("LogNormal", new Expr.Variable(new Token(TokenType.IDENTIFIER, "LogNormal", null, 1)))
                                )
                        )
                )
        );

        testStatement(
                "PositiveReal value ~ LogNormal(meanLog = 10.5, sdLog)",
                new Stmt.Draw(
                        new Type.Atomic("PositiveReal"),
                        "value",
                        new Expr.Call(
                                new Expr.Variable(new Token(TokenType.IDENTIFIER, "LogNormal", null, 1)),
                                new Expr.Argument("meanLog", new Expr.Literal(10.5)),
                                new Expr.Argument("sdLog", new Expr.Variable(new Token(TokenType.IDENTIFIER, "sdLog", null, 1)))
                        )
                )
        );
    }

    void testStatement(String source, Stmt expectedStatement) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> actualStatements = parser.parse();

        assertEquals(1, actualStatements.size());
        assertEquals(expectedStatement, actualStatements.get(0));
    }
}
