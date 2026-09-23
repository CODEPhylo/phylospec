package org.phylospec.components;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests the validation of declared type property constraints which is performed when a component
 * library is registered.
 */
public class ComponentResolverConstraintTest {

    @Test
    public void testValidConstraintsAreAccepted() {
        // Simplex inherits `num` from Vector, and Rate aliases PositiveReal which inherits `value` from Real

        assertDoesNotThrow(() -> resolverWithConstraints("""
                {"argument": "left", "property": "num", "operator": "equals", "otherArgument": "right", "otherProperty": "num"},
                {"argument": "simplex", "property": "num", "operator": "greaterThan", "constant": 2},
                {"argument": "rate", "property": "value", "operator": "lessThan", "otherArgument": "simplex", "otherProperty": "num"}
                """));
    }

    @Test
    public void testGenericArgumentTypesAreSkipped() {
        // we cannot know the type properties of a generic argument, so we don't check them

        assertDoesNotThrow(() -> resolverWithGenericConstraints("""
                {"argument": "generic", "property": "whatever", "operator": "equals", "constant": 1}
                """));
    }

    @Test
    public void testMalformedRightHandSide() {
        String bothSides = assertInvalid("""
                {"argument": "left", "property": "num", "operator": "equals", "otherArgument": "right", "otherProperty": "num", "constant": 2}
                """);
        assertTrue(bothSides.contains("Only use one."), bothSides);

        String missingProperty = assertInvalid("""
                {"argument": "left", "property": "num", "operator": "equals", "otherArgument": "right"}
                """);
        assertTrue(missingProperty.contains("has to specify both the other argument"), missingProperty);
    }

    @Test
    public void testUnknownArgument() {
        String unknownLeft = assertInvalid("""
                {"argument": "missing", "property": "num", "operator": "equals", "constant": 2}
                """);
        assertTrue(unknownLeft.contains("Unknown argument in constraint for constrained: missing"), unknownLeft);

        String unknownRight = assertInvalid("""
                {"argument": "left", "property": "num", "operator": "equals", "otherArgument": "missing", "otherProperty": "num"}
                """);
        assertTrue(unknownRight.contains("Unknown argument in constraint for constrained: missing"), unknownRight);
    }

    @Test
    public void testUnknownTypeProperty() {
        String unknownLeft = assertInvalid("""
                {"argument": "left", "property": "numRows", "operator": "equals", "constant": 2}
                """);
        assertTrue(
                unknownLeft.contains("Unknown type property in constraint for constrained: left.numRows"), unknownLeft);
        assertTrue(unknownLeft.contains("[num]"), unknownLeft);

        String unknownRight = assertInvalid("""
                {"argument": "left", "property": "num", "operator": "equals", "otherArgument": "rate", "otherProperty": "num"}
                """);
        assertTrue(
                unknownRight.contains("Unknown type property in constraint for constrained: rate.num"), unknownRight);
    }

    /**
     * Registers a library declaring the given constraints and returns the message of the resulting error.
     */
    private static String assertInvalid(String constraintsJson) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> resolverWithConstraints(constraintsJson));
        return exception.getMessage();
    }

    /**
     * Registers a generator declaring the given constraints on top of the core component library.
     */
    private static ComponentResolver resolverWithConstraints(String constraintsJson) throws IOException {
        return resolverWithGenerator("""
                {
                  "name": "constrained",
                  "namespace": "phylospec.test",
                  "description": "Generator used to test constraint validation.",
                  "generatedType": "Real",
                  "arguments": [
                    {"name": "left", "type": "Vector<Real>", "description": "Left vector.", "required": true},
                    {"name": "right", "type": "Vector<Real>", "description": "Right vector.", "required": true},
                    {"name": "simplex", "type": "Simplex", "description": "Inherits num from Vector.", "required": true},
                    {"name": "rate", "type": "Rate", "description": "Aliases PositiveReal.", "required": true}
                  ],
                  "constraints": [%s]
                }
                """.formatted(constraintsJson));
    }

    /**
     * Same as {@link #resolverWithConstraints}, but the constrained argument has a generic type.
     */
    private static ComponentResolver resolverWithGenericConstraints(String constraintsJson) throws IOException {
        return resolverWithGenerator("""
                {
                  "name": "constrained",
                  "namespace": "phylospec.test",
                  "description": "Generator used to test constraint validation.",
                  "generatedType": "Real",
                  "typeParameters": ["T"],
                  "arguments": [
                    {"name": "generic", "type": "T", "description": "Generic argument.", "required": true}
                  ],
                  "constraints": [%s]
                }
                """.formatted(constraintsJson));
    }

    private static ComponentResolver resolverWithGenerator(String generatorJson) throws IOException {
        String libraryJson = """
                {
                  "$schema": "https://phylospec.org/schemas/component-library-schema-v5.json",
                  "componentLibrary": {
                    "name": "Constraint Test Library",
                    "version": "1.0.0",
                    "engine": "PhyloSpec",
                    "engineVersion": "1.0.0",
                    "description": "Library used to test constraint validation.",
                    "types": [],
                    "generators": [%s]
                  }
                }
                """.formatted(generatorJson);

        List<ComponentLibrary> libraries = new ArrayList<>(ComponentResolver.loadCoreComponentLibraries());
        libraries.add(ComponentResolver.loadLibraryFromInputStream(
                new ByteArrayInputStream(libraryJson.getBytes(StandardCharsets.UTF_8))));

        return new ComponentResolver(libraries);
    }
}
