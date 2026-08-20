package org.phylospec.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ParsedTypeConstraintTest {
    @Test
    public void testGetUnqualifiedNameWithQualifiedNestedGenerics() {
        ParsedTypeConstraint.PropertyComparison parsedTypeConstraint = parseProperties("x.a == y.b");
        assertEquals(ParsedTypeConstraint.ConstraintType.EQUALITY, parsedTypeConstraint.getConstraintType());
        assertEquals("x", parsedTypeConstraint.getLeftInputName());
        assertEquals("a", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("y", parsedTypeConstraint.getRightInputName());
        assertEquals("b", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = parseProperties("x.a != y.b");
        assertEquals(ParsedTypeConstraint.ConstraintType.INEQUALITY, parsedTypeConstraint.getConstraintType());
        assertEquals("x", parsedTypeConstraint.getLeftInputName());
        assertEquals("a", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("y", parsedTypeConstraint.getRightInputName());
        assertEquals("b", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = parseProperties("input1.aaa > input2.bbb");
        assertEquals(ParsedTypeConstraint.ConstraintType.GREATER, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input2", parsedTypeConstraint.getRightInputName());
        assertEquals("bbb", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = parseProperties("input1.aaa >= input2.bbb");
        assertEquals(ParsedTypeConstraint.ConstraintType.GREATER_THAN, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input2", parsedTypeConstraint.getRightInputName());
        assertEquals("bbb", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = parseProperties("input1.aaa < input1.aaa");
        assertEquals(ParsedTypeConstraint.ConstraintType.LESS, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input1", parsedTypeConstraint.getRightInputName());
        assertEquals("aaa", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = parseProperties("input1.aaa =< input1.aaa");
        assertEquals(ParsedTypeConstraint.ConstraintType.LESS_THAN, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input1", parsedTypeConstraint.getRightInputName());
        assertEquals("aaa", parsedTypeConstraint.getRightPropertyName());

        ParsedTypeConstraint.ConstantComparison constantComparison = parseConstant("baseFrequencies.num == 4");
        assertEquals(ParsedTypeConstraint.ConstraintType.EQUALITY, constantComparison.getConstraintType());
        assertEquals("baseFrequencies", constantComparison.getLeftInputName());
        assertEquals("num", constantComparison.getLeftPropertyName());
        assertEquals(4, constantComparison.getConstant());

        constantComparison = parseConstant("input1.aaa >= -0.5");
        assertEquals(ParsedTypeConstraint.ConstraintType.GREATER_THAN, constantComparison.getConstraintType());
        assertEquals("input1", constantComparison.getLeftInputName());
        assertEquals("aaa", constantComparison.getLeftPropertyName());
        assertEquals(-0.5, constantComparison.getConstant());
    }

    @Test
    public void testRejectsMalformedConstraints() {
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse(""));
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("x.a =="));
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("x.a == y.b.c"));
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("x.a == 4four"));
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("x.a ~ y.b"));
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("x == y.b"));

        // the left side always has to be an input property

        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("4 == 4"));
        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("4 == x.a"));

        // inputs are no longer prefixed with a dollar

        assertThrows(IllegalArgumentException.class, () -> ParsedTypeConstraint.parse("$x.a == $y.b"));
    }

    private static ParsedTypeConstraint.PropertyComparison parseProperties(String constraint) {
        return assertInstanceOf(ParsedTypeConstraint.PropertyComparison.class, ParsedTypeConstraint.parse(constraint));
    }

    private static ParsedTypeConstraint.ConstantComparison parseConstant(String constraint) {
        return assertInstanceOf(ParsedTypeConstraint.ConstantComparison.class, ParsedTypeConstraint.parse(constraint));
    }
}
