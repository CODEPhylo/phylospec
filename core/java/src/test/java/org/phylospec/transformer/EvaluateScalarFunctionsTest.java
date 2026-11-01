package org.phylospec.transformer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.phylospec.ast.AstType;
import org.phylospec.ast.Expr;
import org.phylospec.ast.Stmt;
import org.phylospec.ast.Unit;
import org.phylospec.ast.transformers.EvaluateLiterals;
import org.phylospec.ast.transformers.EvaluateScalarFunctions;
import org.phylospec.ast.transformers.RemoveGroupings;
import org.phylospec.components.ComponentResolver;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeError;
import org.phylospec.typeresolver.TypeResolver;

public class EvaluateScalarFunctionsTest {

    @Test
    public void testLog() {
        testStatements(
                "Real a = log(10)",
                new Stmt.Assignment(new AstType.Atomic("Real"), "a", new Expr.Literal(2.302585092994046)));
        testStatements(
                "Real a ~ Exponential(log(x=10, base=10))",
                new Stmt.Draw(
                        new AstType.Atomic("Real"),
                        "a",
                        new Expr.Call("Exponential", new Expr.AssignedArgument(null, new Expr.Literal(1.0)))));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "log(x=0.5) | -0.6931471805599453",
                "log(x=1.0) | 0.0",
                "log(x=2.0) | 0.6931471805599453",
                "log(x=8, base=2) | 3.0",
                "log(x=100, base=10) | 2.0",
                "log(base=2, x=8) | 3.0",
                "log(x=log(x=100, base=10), base=2) | 1.0"
            })
    void validLiteralLogarithmsAreFolded(String call, double expected) throws Exception {
        List<Stmt> statements = parseNormalized("Real a = " + call);
        Stmt.Assignment original = assertInstanceOf(Stmt.Assignment.class, statements.getFirst());
        var originalRange = original.expression.getRange();
        assertNotNull(originalRange);
        statements = new EvaluateScalarFunctions().transform(statements);
        Stmt.Assignment assignment = assertInstanceOf(Stmt.Assignment.class, statements.getFirst());
        Expr.Literal result = assertInstanceOf(Expr.Literal.class, assignment.expression);
        assertEquals(expected, ((Number) result.value).doubleValue(), 1e-14);
        assertEquals(originalRange, result.getRange());
        resolve(statements);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "log(x=100, base=-2) | false",
                "log(x=100, base=0) | false",
                "log(x=100, base=1) | false",
                "log(x=100, base=0.5) | true",
                "log(x=100, base=2.0) | true",
                "log(x=0) | true",
                "log(x=-1) | true",
                "log(x=100, typo=10) | true",
                "log(x=100, x=10) | true",
                "log(x=100, base=2, base=10) | true",
                "log(base=10) | true",
                "log(100, 10) | true",
                "log(10, base=10) | true",
                "log(x~100) | true"
            })
    void invalidLiteralLogarithmsRemainCallsForValidation(String call, boolean rejectedByCore) throws Exception {
        List<Stmt> statements = assertCallPreserved("Real a = " + call);
        if (rejectedByCore) {
            assertThrows(TypeError.class, () -> resolve(statements));
        } else {
            // Core declares base as Integer; its mathematical restriction remains a backend check.
            assertDoesNotThrow(() -> resolve(statements));
        }
    }

    @Test
    void invalidNestedLogarithmRemainsAvailableForValidation() {
        List<Stmt> statements = assertCallPreserved("Real a = log(x=log(x=100, base=1), base=2)");
        Stmt.Assignment assignment = assertInstanceOf(Stmt.Assignment.class, statements.getFirst());
        Expr.Call outer = assertInstanceOf(Expr.Call.class, assignment.expression);
        assertInstanceOf(Expr.Call.class, outer.arguments[0].expression);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PositiveReal x = 10.0\nReal a = log(x=x)",
                "Integer base = 10\nReal a = log(x=100, base=base)",
                "Integer base ~ DiscreteUniform(lower=2, upper=10)\nReal a = log(x=100, base=base)"
            })
    void variableAndRandomInputsAreNotFolded(String source) throws Exception {
        List<Stmt> statements = assertCallPreserved(source);
        assertDoesNotThrow(() -> resolve(statements));
    }

    @Test
    void explicitlyUnitBearingArgumentsAreNotFolded() {
        for (boolean unitOnBase : new boolean[] {false, true}) {
            Expr.Literal x = new Expr.Literal(100, unitOnBase ? Unit.IMPLICIT : Unit.YEARS);
            Expr.Literal base = new Expr.Literal(10, unitOnBase ? Unit.YEARS : Unit.IMPLICIT);
            Expr.Call call =
                    new Expr.Call("log", new Expr.AssignedArgument("x", x), new Expr.AssignedArgument("base", base));
            Expr actual = call.accept(new EvaluateScalarFunctions());
            assertSame(call, actual);
            assertEquals(Unit.YEARS, (unitOnBase ? base : x).unit);
        }
    }

    @Test
    public void testExp() {
        testStatements(
                "Real a = exp(10)",
                new Stmt.Assignment(new AstType.Atomic("Real"), "a", new Expr.Literal(22026.465794806718)));
        testStatements(
                "Real a ~ Exponential(exp(0))",
                new Stmt.Draw(
                        new AstType.Atomic("Real"),
                        "a",
                        new Expr.Call("Exponential", new Expr.AssignedArgument(null, new Expr.Literal(1.0)))));
    }

    @Test
    public void testSqrt() {
        testStatements("Real a = sqrt(4)", new Stmt.Assignment(new AstType.Atomic("Real"), "a", new Expr.Literal(2.0)));
        testStatements(
                "Real a ~ Exponential(sqrt(0))",
                new Stmt.Draw(
                        new AstType.Atomic("Real"),
                        "a",
                        new Expr.Call("Exponential", new Expr.AssignedArgument(null, new Expr.Literal(0.0)))));
    }

    private static List<Stmt> assertCallPreserved(String source) {
        // Parse twice because AST transformers may mutate their input statements.
        List<Stmt> expected = parseNormalized(source);
        List<Stmt> actual = new EvaluateScalarFunctions().transform(parseNormalized(source));
        Stmt.Assignment assignment = assertInstanceOf(Stmt.Assignment.class, actual.getLast());
        assertInstanceOf(Expr.Call.class, assignment.expression);
        assertEquals(expected, actual);
        return actual;
    }

    private static List<Stmt> parseNormalized(String source) {
        Lexer lexer = new Lexer(source);
        lexer.registerEventListener(error -> fail(error.toStdOutString(source)));
        Parser parser = new Parser(lexer.scanTokens());
        parser.registerEventListener(error -> fail(error.toStdOutString(source)));
        return new EvaluateLiterals().transform(new RemoveGroupings().transform(parser.parse()));
    }

    private static void resolve(List<Stmt> statements) throws Exception {
        TypeResolver resolver = new TypeResolver(new ComponentResolver(ComponentResolver.loadCoreComponentLibraries()));
        resolver.visitStatements(statements);
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
