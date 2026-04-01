package org.phylospec.typeresolver;

import org.junit.jupiter.api.Test;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;

import java.util.List;

public class VariableResolverTest {
    @Test
    public void testAssignmentsWithoutScopes() {
        String model = """
                Real a = 10
                Real b = a * 100
                String c = "Test${b}"
                """;

        List<Token> tokens = new Lexer(model).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();

        Expr.Variable a = (Expr.Variable) ((Expr.Binary) ((Stmt.Assignment) statements.get(1)).expression).left;
        Expr.Variable b = ((Expr.StringTemplate.ExpressionPart) ((Expr.StringTemplate) ((Stmt.Assignment) statements.get(2)).expression).parts.get(1)).expression();

        VariableResolver variableResolver = new VariableResolver(statements);

        // test that object equality is not enough

        assert variableResolver.resolveVariable(new Expr.Variable("a")) == null;
        assert variableResolver.resolveVariable(new Expr.Variable("b")) == null;

        // look up actual AST nodes

        assert variableResolver.resolveVariable(a) == statements.getFirst();
        assert variableResolver.resolveVariable(b) == statements.get(1);
    }

    @Test
    public void testDrawsWithoutScopes() {
        String model = """
                Real a = 10
                Real b ~ Normal(mean=a, sd=a)
                String c = [b]
                """;

        List<Token> tokens = new Lexer(model).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();

        Expr.Variable a1 = (Expr.Variable) ((Expr.Call) ((Stmt.Draw) statements.get(1)).expression).arguments[0].expression;
        Expr.Variable a2 = (Expr.Variable) ((Expr.Call) ((Stmt.Draw) statements.get(1)).expression).arguments[1].expression;
        Expr.Variable b = (Expr.Variable) ((Expr.Array) ((Stmt.Assignment) statements.get(2)).expression).elements.getFirst();

        VariableResolver variableResolver = new VariableResolver(statements);

        // test that object equality is not enough

        assert variableResolver.resolveVariable(new Expr.Variable("a")) == null;
        assert variableResolver.resolveVariable(new Expr.Variable("b")) == null;

        // look up actual AST nodes

        assert variableResolver.resolveVariable(a1) == statements.getFirst();
        assert variableResolver.resolveVariable(a2) == statements.getFirst();
        assert variableResolver.resolveVariable(b) == statements.get(1);
    }

    @Test
    public void testScopes() {
        String model = """
                Real a = 10
                Real b[a] = a * 100 for a in 1:a
                """;

        List<Token> tokens = new Lexer(model).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();

        Expr.Variable aRange = (Expr.Variable) ((Expr.Range) ((Stmt.Indexed) statements.get(1)).ranges.get(0)).to;
        Expr.Variable aIndex = ((Stmt.Indexed) statements.get(1)).indices.getFirst();
        Expr.Variable aInner = (Expr.Variable) ((Expr.Binary) ((Stmt.Assignment) ((Stmt.Indexed) statements.get(1)).statement).expression).left;

        VariableResolver variableResolver = new VariableResolver(statements);

        // test that object equality is not enough

        assert variableResolver.resolveVariable(new Expr.Variable("a")) == null;

        // look up actual AST nodes

        assert variableResolver.resolveVariable(aRange) == statements.getFirst();
        assert variableResolver.resolveVariable(aIndex) == null;
        assert variableResolver.resolveVariable(aInner) == null;
    }
}
