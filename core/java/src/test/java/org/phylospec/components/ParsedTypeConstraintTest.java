package org.phylospec.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ParsedTypeConstraintTest {
    @Test
    public void testGetUnqualifiedNameWithQualifiedNestedGenerics() {
        ParsedTypeConstraint parsedTypeConstraint = new ParsedTypeConstraint("x.a == y.b");
        assertEquals(ParsedTypeConstraint.ConstraintType.EQUALITY, parsedTypeConstraint.getConstraintType());
        assertEquals("x", parsedTypeConstraint.getLeftInputName());
        assertEquals("a", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("y", parsedTypeConstraint.getRightInputName());
        assertEquals("b", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = new ParsedTypeConstraint("x.a != y.b");
        assertEquals(ParsedTypeConstraint.ConstraintType.INEQUALITY, parsedTypeConstraint.getConstraintType());
        assertEquals("x", parsedTypeConstraint.getLeftInputName());
        assertEquals("a", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("y", parsedTypeConstraint.getRightInputName());
        assertEquals("b", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = new ParsedTypeConstraint("input1.aaa > input2.bbb");
        assertEquals(ParsedTypeConstraint.ConstraintType.GREATER, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input2", parsedTypeConstraint.getRightInputName());
        assertEquals("bbb", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = new ParsedTypeConstraint("input1.aaa >= input2.bbb");
        assertEquals(ParsedTypeConstraint.ConstraintType.GREATER_THAN, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input2", parsedTypeConstraint.getRightInputName());
        assertEquals("bbb", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = new ParsedTypeConstraint("input1.aaa < input1.aaa");
        assertEquals(ParsedTypeConstraint.ConstraintType.LESS, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input1", parsedTypeConstraint.getRightInputName());
        assertEquals("aaa", parsedTypeConstraint.getRightPropertyName());

        parsedTypeConstraint = new ParsedTypeConstraint("input1.aaa =< input1.aaa");
        assertEquals(ParsedTypeConstraint.ConstraintType.LESS_THAN, parsedTypeConstraint.getConstraintType());
        assertEquals("input1", parsedTypeConstraint.getLeftInputName());
        assertEquals("aaa", parsedTypeConstraint.getLeftPropertyName());
        assertEquals("input1", parsedTypeConstraint.getRightInputName());
        assertEquals("aaa", parsedTypeConstraint.getRightPropertyName());
    }
}
