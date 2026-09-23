package org.phylospec.typeresolver.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.phylospec.ast.Stmt;
import org.phylospec.components.ComponentLibrary;
import org.phylospec.components.ComponentResolver;
import org.phylospec.errors.Error;
import org.phylospec.errors.ErrorEventListener;
import org.phylospec.lexer.Lexer;
import org.phylospec.lexer.Token;
import org.phylospec.parser.Parser;
import org.phylospec.typeresolver.TypeResolver;

/**
 * Tests how declared type property constraints are evaluated when a generator is applied.
 * The tests use a generator taking two vectors, so that the number of their elements can be
 * compared either with a constant or with each other.
 */
public class GeneratorConstraintTest {

    @Test
    public void testComparisonWithConstant() throws IOException {
        assertAllOperators(GeneratorConstraintTest::constantConstraint);
    }

    @Test
    public void testComparisonWithOtherArgument() throws IOException {
        assertAllOperators(GeneratorConstraintTest::propertyConstraint);
    }

    @Test
    public void testWarningDescribesTheViolatedConstraint() throws IOException {
        Error constantWarning = assertSingleWarning(constantConstraint("equals"), 3);

        assertEquals("The inputs for 'constrained' might be invalid.", constantWarning.description());
        assertEquals("The number of elements in 'left' must be equal to 2.", constantWarning.hint());

        Error propertyWarning = assertSingleWarning(propertyConstraint("greaterThanOrEqual"), 1);

        assertEquals(
                "The number of elements in 'left' must be greater than or equal to the number of elements in 'right'.",
                propertyWarning.hint());
    }

    @Test
    public void testUnknownPropertyValuesAreIgnored() throws IOException {
        // `right` is optional, so its number of elements is unknown and the constraint cannot be evaluated

        String script = """
                Vector<Real> a = [1.0]
                Real result = constrained(left=a)
                """;

        assertTrue(collectWarnings(propertyConstraint("equals"), script).isEmpty());
    }

    /**
     * Checks every operator against a right-hand side of two, once with a satisfying and once with
     * a violating number of elements on the left-hand side.
     */
    private static void assertAllOperators(Function<String, String> constraint) throws IOException {
        assertWarning(constraint.apply("equals"), 2, false);
        assertWarning(constraint.apply("equals"), 3, true);
        assertWarning(constraint.apply("notEquals"), 3, false);
        assertWarning(constraint.apply("notEquals"), 2, true);
        assertWarning(constraint.apply("lessThan"), 1, false);
        assertWarning(constraint.apply("lessThan"), 2, true);
        assertWarning(constraint.apply("lessThanOrEqual"), 2, false);
        assertWarning(constraint.apply("lessThanOrEqual"), 3, true);
        assertWarning(constraint.apply("greaterThan"), 3, false);
        assertWarning(constraint.apply("greaterThan"), 2, true);
        assertWarning(constraint.apply("greaterThanOrEqual"), 2, false);
        assertWarning(constraint.apply("greaterThanOrEqual"), 1, true);
    }

    private static void assertWarning(String constraintJson, int leftSize, boolean expectWarning) throws IOException {
        List<Error> warnings = collectWarnings(constraintJson, leftSize);
        assertEquals(
                expectWarning ? 1 : 0,
                warnings.size(),
                "Unexpected warnings for " + constraintJson.strip() + " and " + leftSize + " elements: " + warnings);
    }

    private static Error assertSingleWarning(String constraintJson, int leftSize) throws IOException {
        List<Error> warnings = collectWarnings(constraintJson, leftSize);
        assertEquals(1, warnings.size(), "Expected exactly one warning, but got: " + warnings);
        return warnings.getFirst();
    }

    private static String constantConstraint(String operator) {
        return """
                {"argument": "left", "property": "num", "operator": "%s", "constant": 2}
                """.formatted(operator);
    }

    private static String propertyConstraint(String operator) {
        return """
                {"argument": "left", "property": "num", "operator": "%s", "otherArgument": "right", "otherProperty": "num"}
                """.formatted(operator);
    }

    private static List<Error> collectWarnings(String constraintJson, int leftSize) throws IOException {
        String script = """
                Vector<Real> a = %s
                Vector<Real> b = [1.0, 1.0]
                Real result = constrained(left=a, right=b)
                """.formatted("[" + String.join(", ", Collections.nCopies(leftSize, "1.0")) + "]");

        return collectWarnings(constraintJson, script);
    }

    /**
     * Resolves the types of the given script, using a generator declaring the given constraint, and
     * returns the raised warnings.
     */
    private static List<Error> collectWarnings(String constraintJson, String script) throws IOException {
        TypeResolver typeResolver = new TypeResolver(buildResolver(constraintJson));

        List<Error> warnings = new ArrayList<>();
        typeResolver.registerEventListener(new ErrorEventListener() {
            @Override
            public void errorDetected(Error error) {}

            @Override
            public void warningDetected(Error warning) {
                warnings.add(warning);
            }
        });

        List<Token> tokens = new Lexer(script).scanTokens();
        List<Stmt> statements = new Parser(tokens).parse();

        for (Stmt statement : statements) {
            statement.accept(typeResolver);
        }

        return warnings;
    }

    private static ComponentResolver buildResolver(String constraintJson) throws IOException {
        String libraryJson = """
                {
                  "$schema": "https://phylospec.org/schemas/component-library-schema-v5.json",
                  "componentLibrary": {
                    "name": "Constraint Test Library",
                    "version": "1.0.0",
                    "engine": "PhyloSpec",
                    "engineVersion": "1.0.0",
                    "description": "Library used to test constraint evaluation.",
                    "types": [],
                    "generators": [
                      {
                        "name": "constrained",
                        "namespace": "phylospec.test",
                        "description": "Generator used to test constraint evaluation.",
                        "generatedType": "Real",
                        "arguments": [
                          {"name": "left", "type": "Vector<Real>", "description": "Left vector.", "required": true},
                          {"name": "right", "type": "Vector<Real>", "description": "Right vector.", "required": false}
                        ],
                        "constraints": [%s]
                      }
                    ]
                  }
                }
                """.formatted(constraintJson);

        List<ComponentLibrary> libraries = new ArrayList<>(ComponentResolver.loadCoreComponentLibraries());
        libraries.add(ComponentResolver.loadLibraryFromInputStream(
                new ByteArrayInputStream(libraryJson.getBytes(StandardCharsets.UTF_8))));

        return new ComponentResolver(libraries);
    }
}
