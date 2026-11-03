package org.phylospec.parser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.AstPrinter;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.lexer.Lexer;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;
import org.phylospec.typeresolver.VariableResolver;

class UnicodeIdentifiersTest {
    @Test
    void resolvesGreekReferencesIndicesAndStringInterpolation() throws Exception {
        String source = """
                Real μ = 2.0
                Real κ ~ Normal(mean=μ, sd=1.0)
                String label = "κ=${κ}"
                Real θ[ι] = κ * ι for ι in 1:3
                """;
        List<Stmt> statements = parse(source);
        TypeResolver types = resolve(statements);
        assertEquals(Set.of("μ", "κ", "label", "θ"), types.getVariableNames());
        assertTrue(types.resolveVariable("ι").isEmpty(), "The index must remain local to its statement");

        VariableResolver variables = new VariableResolver(statements);
        Expr.Variable mean =
                (Expr.Variable) ((Expr.Call) ((Stmt.Draw) statements.get(1)).expression).arguments[0].expression;
        Expr.Variable interpolated = ((Expr.StringTemplate.ExpressionPart)
                        ((Expr.StringTemplate) ((Stmt.Assignment) statements.get(2)).expression).parts.get(1))
                .expression();
        Stmt.Indexed indexed = (Stmt.Indexed) statements.get(3);
        Expr.Variable shared = (Expr.Variable) ((Expr.Binary) ((Stmt.Assignment) indexed.statement).expression).left;
        assertSame(statements.get(0), variables.resolveVariable(mean));
        assertSame(statements.get(1), variables.resolveVariable(interpolated));
        assertSame(statements.get(1), variables.resolveVariable(shared));
        assertEquals("ι", indexed.indices.getFirst().variableName);
        assertTrue(statements.get(1).accept(new AstPrinter()).contains("κ"));
    }

    @Test
    void preservesDistinctSpellingsWithoutNormalization() throws Exception {
        List<Stmt> statements = parse("""
                Real μ = 1.0
                Real µ = 2.0
                Real é = 3.0
                Real e\u0301 = 4.0
                Real 𝛼 = 5.0
                Real total = μ + µ + é + e\u0301 + 𝛼
                """);
        TypeResolver types = resolve(statements);
        assertEquals(Set.of("μ", "µ", "é", "e\u0301", "𝛼", "total"), types.getVariableNames());
        assertEquals(6, statements.size());
    }

    @Test
    void duplicateUnicodeDeclarationsRemainAnError() throws Exception {
        List<Stmt> statements = parse("Real κ = 1.0\nReal κ = 2.0\n");
        TypeResolver types = new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        statements.getFirst().accept(types);
        TypeError error = assertThrows(TypeError.class, () -> statements.get(1).accept(types));
        assertTrue(error.getMessage().contains("κ"));
    }

    @Test
    void undefinedUnicodeReferencesRemainAnError() {
        TypeError error = assertThrows(TypeError.class, () -> resolve(parse("Real y = θ\n")));
        assertTrue(error.getMessage().contains("θ"));
    }

    @Test
    void aNameCannotStartWithADigit() {
        List<Error> errors = new ArrayList<>();
        Lexer lexer = new Lexer("Real 1κ = 2.0\n");
        lexer.registerEventListener(errors::add);
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(errors::add);
        parser.parse();
        assertFalse(errors.isEmpty());
    }

    private static List<Stmt> parse(String source) {
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return parser.parse();
    }

    private static TypeResolver resolve(List<Stmt> statements) throws Exception {
        TypeResolver resolver = new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        for (Stmt statement : statements) statement.accept(resolver);
        return resolver;
    }
}
